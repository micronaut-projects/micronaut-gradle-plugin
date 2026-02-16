/*
 * Copyright 2003-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.gradle.aot;

import io.micronaut.gradle.AttributeUtils;
import io.micronaut.gradle.MicronautBasePlugin;
import io.micronaut.gradle.MicronautComponentPlugin;
import io.micronaut.gradle.MicronautExtension;
import io.micronaut.gradle.PluginsHelper;
import io.micronaut.gradle.ShadowPluginSupport;
import io.micronaut.gradle.docker.MicronautDockerPlugin;
import io.micronaut.gradle.docker.model.LayerKind;
import io.micronaut.gradle.docker.model.MicronautDockerImage;
import io.micronaut.gradle.docker.model.RuntimeKind;
import org.graalvm.buildtools.gradle.NativeImagePlugin;
import org.graalvm.buildtools.gradle.dsl.GraalVMExtension;
import org.graalvm.buildtools.gradle.dsl.NativeImageOptions;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.distribution.DistributionContainer;
import org.gradle.api.distribution.plugins.DistributionPlugin;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RelativePath;
import org.gradle.api.java.archives.Attributes;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.ApplicationPlugin;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.application.CreateStartScripts;
import org.gradle.api.tasks.bundling.Jar;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.codehaus.groovy.runtime.StringGroovyMethods.capitalize;
import static org.gradle.api.plugins.JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME;

@SuppressWarnings("Convert2Lambda")
public abstract class MicronautAotPlugin implements Plugin<Project> {

    public static final String DEFAULT_AOT_VERSION = Versions.AOT_VERSION;
    public static final String OPTIMIZED_BINARY_NAME = "optimized";
    public static final String OPTIMIZED_DIST_NAME = "optimized";
    public static final String MAIN_BINARY_NAME = "main";

    static final List<String> TYPES_TO_CHECK = List.of(
            "io.reactivex.Observable",
            "reactor.core.publisher.Flux",
            "kotlinx.coroutines.flow.Flow",
            "io.reactivex.rxjava3.core.Flowable",
            "io.reactivex.rxjava3.core.Observable",
            "io.reactivex.Single",
            "reactor.core.publisher.Mono",
            "io.reactivex.Maybe",
            "io.reactivex.rxjava3.core.Single",
            "io.reactivex.rxjava3.core.Maybe",
            "io.reactivex.Completable",
            "io.reactivex.rxjava3.core.Completable",
            "io.methvin.watchservice.MacOSXListeningWatchService",
            "io.micronaut.core.async.publisher.CompletableFuturePublisher",
            "io.micronaut.core.async.publisher.Publishers.JustPublisher",
            "io.micronaut.core.async.subscriber.Completable"
    );

    public static final List<String> SERVICE_TYPES = List.of(
            "io.micronaut.context.env.PropertySourceLoader",
            "io.micronaut.inject.BeanConfiguration",
            "io.micronaut.inject.BeanDefinitionReference",
            "io.micronaut.http.HttpRequestFactory",
            "io.micronaut.http.HttpResponseFactory",
            "io.micronaut.core.beans.BeanIntrospectionReference",
            "io.micronaut.core.convert.TypeConverterRegistrar",
            "io.micronaut.context.env.PropertyExpressionResolver"
    );
    public static final String AOT_APPLICATION_CLASSPATH = "aotApplicationClasspath";
    public static final String OPTIMIZED_RUNTIME_CLASSPATH_CONFIGURATION_NAME = "optimizedRuntimeClasspath";
    public static final String DEFAULT_GENERATED_PACKAGE = "io.micronaut.aot.generated";

    @Inject
    protected abstract ArchiveOperations getArchiveOperations();

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(MicronautBasePlugin.class);
        MicronautExtension micronautExtension = project.getExtensions().getByType(MicronautExtension.class);
        AOTExtension aotExtension = micronautExtension.getExtensions().create("aot", AOTExtension.class);
        configureAotDefaults(project, aotExtension);
        Configurations configurations = prepareConfigurations(project, aotExtension);
        project.getPlugins().withType(MicronautComponentPlugin.class, p -> registerPrepareOptimizationsTasks(project, configurations, aotExtension));
    }

    private void configureAotDefaults(Project project, AOTExtension aotExtension) {
        ProviderFactory providers = project.getProviders();
        aotExtension.getVersion().convention(MicronautAotPlugin.DEFAULT_AOT_VERSION);
        aotExtension.getCacheEnvironment().convention(false);
        aotExtension.getOptimizeServiceLoading().convention(false);
        aotExtension.getConvertYamlToJava().convention(false);
        aotExtension.getReplaceLogbackXml().convention(false);
        aotExtension.getOptimizeClassLoading().convention(false);
        aotExtension.getPrecomputeOperations().convention(false);
        aotExtension.getDeduceEnvironment().convention(false);
        aotExtension.getTargetPackage().convention(providers.provider(() -> {
            JavaApplication javaApplication = project.getExtensions().findByType(JavaApplication.class);
            if (javaApplication != null) {
                String mainClass = javaApplication.getMainClass().get();
                return mainClass.contains(".") ? mainClass.substring(0, mainClass.lastIndexOf('.')) : DEFAULT_GENERATED_PACKAGE;
            }
            return DEFAULT_GENERATED_PACKAGE;
        }));
        aotExtension.getOptimizeNetty().convention(false);
    }

    private void registerPrepareOptimizationsTasks(Project project, Configurations configurations, AOTExtension aotExtension) {
        Configuration optimizerRuntimeClasspath = configurations.aotOptimizerRuntimeClasspath;
        Configuration applicationClasspath = configurations.aotApplicationClasspath;
        TaskContainer tasks = project.getTasks();
        TaskProvider<MicronautAotOptimizerTask> prepareJit = registerPrepareOptimizationTask(project, optimizerRuntimeClasspath, applicationClasspath, tasks, aotExtension, OptimizerIO.TargetRuntime.JIT);
        registerJavaExecOptimizedRun(project, tasks, prepareJit);
        TaskProvider<MicronautAotOptimizerTask> prepareNative = registerPrepareOptimizationTask(project, optimizerRuntimeClasspath, applicationClasspath, tasks, aotExtension, OptimizerIO.TargetRuntime.NATIVE);
        var optimizedNativeJarProvider = registerOptimizedJar(project, tasks, prepareNative, OptimizerIO.TargetRuntime.NATIVE);
        project.getPlugins().withType(NativeImagePlugin.class, p -> registerOptimizedBinary(project, optimizedNativeJarProvider));

        registerCreateSamplesTasks(project, optimizerRuntimeClasspath, applicationClasspath, tasks, aotExtension);
    }

    private void registerCreateSamplesTasks(Project project, Configuration optimizerRuntimeClasspath, Configuration applicationClasspath, TaskContainer tasks, AOTExtension aotExtension) {
        TaskProvider<Task> createAotSampleConfigurationFiles = tasks.register("createAotSampleConfigurationFiles", task ->
            task.setDescription("Generates Micronaut AOT sample configuration files")
        );
        for (OptimizerIO.TargetRuntime targetRuntime : OptimizerIO.TargetRuntime.values()) {
            TaskProvider<MicronautAotSampleConfTask> createSample = tasks.register("createAot" + targetRuntime.getCapitalizedName() + "Sample", MicronautAotSampleConfTask.class, task -> {
                task.setDescription("Creates a sample " + targetRuntime.getCapitalizedName() + " AOT configuration file");
                task.getTargetPackage().convention("sample.app");
                task.getTargetRuntime().set(targetRuntime);
                task.getOptimizerClasspath().from(optimizerRuntimeClasspath);
                task.getClasspath().from(applicationClasspath);
                task.getOutputDirectory().convention(project.getLayout().getBuildDirectory().dir("generated/aot/samples/" + targetRuntime.getSimpleName()));
                task.getAotVersion().convention(aotExtension.getVersion());
                task.getEnvironmentVariables().convention(aotExtension.getEnvironmentVariables());
            });
            createAotSampleConfigurationFiles.configure(c -> c.dependsOn(createSample));
        }
    }

    private void registerOptimizedDistribution(Project project,
                                               TaskProvider<Jar> optimizedJar) {
        DistributionContainer distributions = project.getExtensions().getByType(DistributionContainer.class);
        JavaApplication appConvention = project.getExtensions().getByType(JavaApplication.class);
        ConfigurableFileCollection classpath = project.getObjects().fileCollection();
        classpath.from(optimizedJar);
        classpath.from(project.getConfigurations().getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME));
        TaskProvider<CreateStartScripts> startScripts = project.getTasks().register("create" + capitalize(OPTIMIZED_DIST_NAME) + "StartScripts", CreateStartScripts.class, task -> {
            JavaApplication javaApplication = project.getExtensions().getByType(JavaApplication.class);
            task.setDescription("Creates OS specific scripts to run the AOT optimized application as a JVM application.");
            task.setClasspath(classpath);
            task.getMainClass().set(javaApplication.getMainClass());
            task.getConventionMapping().map("applicationName", appConvention::getApplicationName);
            task.getConventionMapping().map("outputDir", () -> new File(project.getLayout().getBuildDirectory().getAsFile().get(), "optimizedScripts"));
            task.getConventionMapping().map("executableDir", appConvention::getExecutableDir);
            task.getConventionMapping().map("defaultJvmOpts", appConvention::getApplicationDefaultJvmArgs);
        });
        distributions.register(OPTIMIZED_DIST_NAME, dist -> dist.contents(contents -> {
            contents.into("bin", spec -> spec.from(startScripts));
            contents.into("lib", spec -> spec.from(classpath));
        }));
    }

    @SuppressWarnings("unchecked")
    private void registerDockerImage(Project project, TaskProvider<Jar> optimizedJar, OptimizerIO.TargetRuntime runtime) {
        MicronautExtension micronautExtension = project.getExtensions().getByType(MicronautExtension.class);
        NamedDomainObjectContainer<MicronautDockerImage> dockerImages = (NamedDomainObjectContainer<MicronautDockerImage>) micronautExtension.getExtensions().getByName("dockerImages");
        TaskContainer tasks = project.getTasks();
        TaskProvider<Jar> optimizedRunnerJar = tasks.register("optimizedRunner" + runtime.getCapitalizedName() + "Jar", Jar.class, jar -> {
            jar.from(getArchiveOperations().zipTree(optimizedJar.map(Jar::getArchiveFile)));
            jar.getArchiveClassifier().set("optimized-runner");
            jar.manifest(manifest -> {
                JavaApplication javaApplication = project.getExtensions().getByType(JavaApplication.class);
                Attributes attrs = manifest.getAttributes();
                attrs.put("Main-Class", javaApplication.getMainClass());
                attrs.put("Class-Path", project.getProviders().provider(() -> {
                    var classpath = new ArrayList<String>();
                    Configuration runtimeClasspath = project.getConfigurations()
                            .getByName(RUNTIME_CLASSPATH_CONFIGURATION_NAME);

                    for (File file : runtimeClasspath) {
                        classpath.add("libs/" + file.getName());
                    }
                    classpath.add("resources/");
                    classpath.add("classes/");
                    return String.join(" ", classpath);
                }));
                manifest.attributes(attrs);
            });
        });
        MicronautDockerImage optimized = dockerImages.findByName("optimized");
        if (optimized != null) {
            // only consider the main app
            optimized.addLayer(layer -> {
                layer.getLayerKind().set(LayerKind.APP);
                layer.getRuntimeKind().set(runtime == OptimizerIO.TargetRuntime.JIT ? RuntimeKind.JIT : RuntimeKind.NATIVE);
                layer.getFiles().from(optimizedRunnerJar);
            });
        } else {
            dockerImages.create("optimized", image -> {
                MicronautDockerPlugin.createDependencyLayers(image, project.getConfigurations().getByName(RUNTIME_CLASSPATH_CONFIGURATION_NAME));
                image.addLayer(layer -> {
                    layer.getLayerKind().set(LayerKind.APP);
                    layer.getRuntimeKind().set(runtime == OptimizerIO.TargetRuntime.JIT ? RuntimeKind.JIT : RuntimeKind.NATIVE);
                    layer.getFiles().from(optimizedRunnerJar);
                });
            });
        }
    }

    private void registerOptimizedBinary(Project project, TaskProvider<Jar> optimizedJar) {
        GraalVMExtension graalVMExtension = project.getExtensions().getByType(GraalVMExtension.class);
        NamedDomainObjectContainer<NativeImageOptions> binaries = graalVMExtension.getBinaries();

        // AOT-specific native-image arguments required for GraalVM 25+.
        // Apply to both the default `main` binary (nativeCompile/nativeRun) and the AOT `optimized` binary
        // (nativeOptimizedCompile/nativeOptimizedRun).
        binaries.named(MAIN_BINARY_NAME, mainBinary ->
                mainBinary.buildArgs("--initialize-at-build-time=io.micronaut.context.ApplicationContextConfigurer$1")
        );

        binaries.create(OPTIMIZED_BINARY_NAME, binary -> {
            var mainSourceSet = PluginsHelper.findSourceSets(project).getByName(SourceSet.MAIN_SOURCE_SET_NAME);
            NativeImageOptions main = binaries.getByName(MAIN_BINARY_NAME);
            binary.getMainClass().set(main.getMainClass());
            binary.getClasspath().from(optimizedJar);
            binary.getClasspath().from(project.getConfigurations().findByName(mainSourceSet.getRuntimeClasspathConfigurationName()));
            // GraalVM 25 enables strict image heap by default, which breaks AOT optimized native builds.
            // See https://github.com/micronaut-projects/micronaut-gradle-plugin/pull/1214
            binary.buildArgs("-H:-StrictImageHeap");
            binary.buildArgs("--initialize-at-build-time=io.micronaut.context.ApplicationContextConfigurer$1");
            // The following lines are a hack for the fact that the GraalVM plugin doesn't configure all binaries
            // to use the metadata repository, but only the ones that it knows about (`main` and `test`).
            binary.getConfigurationFileDirectories().from(main.getConfigurationFileDirectories());
            binary.getExcludeConfigArgs().convention(main.getExcludeConfigArgs());
            project.getPlugins().withId("java-library", p -> binary.getSharedLibrary().convention(true));
        });
    }

    private TaskProvider<Jar> registerOptimizedJar(Project project,
                                                   TaskContainer tasks,
                                                   TaskProvider<MicronautAotOptimizerTask> prepareTask,
                                                   MicronautAotOptimizerTask.TargetRuntime runtime) {
        TaskProvider<Jar> mainJar = tasks.named("jar", Jar.class);
        TaskProvider<MergeServiceFiles> mergeTask = tasks.register("mergeServiceFilesForOptimized" + runtime.getCapitalizedName() + "Jar", MergeServiceFiles.class, task -> {
            task.getInputFiles().from(mainJar.map(jar -> getArchiveOperations().zipTree(jar.getArchiveFile().get().getAsFile())));
            task.getInputFiles().from(prepareTask.flatMap(MicronautAotOptimizerTask::getGeneratedClassesDirectory));
            task.getOutputDirectory().convention(project.getLayout().getBuildDirectory().dir("generated/aot/" + runtime.getSimpleName() + "-service-files"));
        });
        TaskProvider<Jar> jarTask = tasks.register("optimized" + runtime.getCapitalizedName() + "Jar", Jar.class, jar -> {
            jar.getInputs().file(prepareTask.map(MicronautAotOptimizerTask::getGeneratedOutputResourceFilter));
            jar.getArchiveClassifier().convention(runtime.getSimpleName());
            jar.from(mainJar.get().getSource(), spec -> spec.eachFile(
                    new JarExclusionSpec(
                            prepareTask.flatMap(MicronautAotOptimizerTask::getGeneratedOutputResourceFilter),
                            Collections.singleton("META-INF/services/"),
                            jar.getLogger()))
            );
            jar.from(prepareTask.map(MicronautAotOptimizerTask::getGeneratedClassesDirectory), spec -> spec.exclude("META-INF/services/**"));
            jar.from(mergeTask);
        });
        tasks.named("assemble").configure(assemble -> assemble.dependsOn(jarTask));
        project.getPlugins().withType(MicronautDockerPlugin.class, p -> registerDockerImage(project, jarTask, runtime));
        return jarTask;
    }

    private void registerJavaExecOptimizedRun(Project project,
                                              TaskContainer tasks,
                                              TaskProvider<MicronautAotOptimizerTask> prepareJit) {
        TaskProvider<Jar> jarTask = registerOptimizedJar(project, tasks, prepareJit, OptimizerIO.TargetRuntime.JIT);
        ShadowPluginSupport.withShadowPlugin(project, () -> AotShadowSupport.registerShadowJar(project, getArchiveOperations(), tasks, jarTask));
        project.getPlugins().withType(DistributionPlugin.class, p -> registerOptimizedDistribution(project, jarTask));
        project.getPlugins().withType(ApplicationPlugin.class, p -> {
            ConfigurationContainer configurations = project.getConfigurations();
            Configuration optimizedRuntimeClasspath = configurations.create(OPTIMIZED_RUNTIME_CLASSPATH_CONFIGURATION_NAME, conf -> {
                Configuration runtimeClasspath = configurations.getByName("runtimeClasspath");
                conf.extendsFrom(runtimeClasspath);
                conf.setCanBeConsumed(false);
                conf.setCanBeConsumed(true);
                // Use the same attributes as runtimeClasspath for resolution
                AttributeUtils.copyAttributes(project.getProviders(), runtimeClasspath, conf);
            });

            tasks.register("optimizedRun", JavaExec.class, task -> {
                JavaApplication javaApplication = project.getExtensions().getByType(JavaApplication.class);
                task.setDescription("Executes the Micronaut application with AOT optimizations");
                task.getMainClass().convention(javaApplication.getMainClass());
                // https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/385
                task.getOutputs().upToDateWhen(t -> false);
                task.setClasspath(project.files(jarTask, optimizedRuntimeClasspath));
                task.doFirst(new Action<>() {
                    @Override
                    public void execute(Task t) {
                        if (task.getLogger().isDebugEnabled()) {
                            task.getLogger().debug(
                                    "Running optimized entry point: {}\nClasspath:\n    {}",
                                    task.getMainClass().get(), task.getClasspath().getFiles()
                                            .stream()
                                            .map(File::getName)
                                            .collect(Collectors.joining("\n    "))
                            );
                        }
                    }
                });
            });
        });
    }

    private TaskProvider<MicronautAotOptimizerTask> registerPrepareOptimizationTask(Project project,
                                                                                    Configuration optimizerClasspath,
                                                                                    Configuration applicationClasspath,
                                                                                    TaskContainer tasks,
                                                                                    AOTExtension aotExtension,
                                                                                    MicronautAotOptimizerTask.TargetRuntime runtime) {
        String runtimeName = runtime.getSimpleName();
        String writeConfigTaskName = "write" + runtime.getCapitalizedName() + "AOTConfig";
        TaskProvider<MicronautAOTConfigWriterTask> configTask = tasks.register(writeConfigTaskName, MicronautAOTConfigWriterTask.class, task -> {
            task.getUserConfiguration().convention(aotExtension.getConfigFile());
            task.getAOTOptimizations().convention(aotExtension);
            task.getForNative().set(runtime == OptimizerIO.TargetRuntime.NATIVE);
            task.getOutputFile().convention(project.getLayout().getBuildDirectory().file("generated/aot/" + runtimeName + ".properties"));
        });
        String prepareTaskName = "prepare" + capitalize(runtimeName) + "Optimizations";
        return tasks.register(prepareTaskName, MicronautAotOptimizerTask.class, task -> {
            task.getOptimizerClasspath().from(optimizerClasspath);
            task.getConfigurationFile().convention(configTask.flatMap(MicronautAOTConfigWriterTask::getOutputFile));
            Provider<Directory> baseDir = project.getLayout().getBuildDirectory().dir("generated/aot/" + runtimeName);
            task.getOutputDirectory().convention(baseDir);
            task.getTargetRuntime().value(runtime).finalizeValue();
            task.getTargetPackage().convention(aotExtension.getTargetPackage());
            task.getClasspath().from(applicationClasspath);
            task.getAotVersion().convention(aotExtension.getVersion());
            task.getEnvironmentVariables().convention(aotExtension.getEnvironmentVariables());
        });
    }

    private Configurations prepareConfigurations(Project project, AOTExtension aotExtension) {
        ConfigurationContainer configurations = project.getConfigurations();
        // Internal configurations
        var providers = project.getProviders();
        Configuration aotOptimizerRuntimeClasspath = configurations.create("aotOptimizerRuntimeClasspath", c -> {
            configureAsRuntimeClasspath(providers, configurations, c);
            c.getDependencies().addLater(aotExtension.getVersion().map(v -> project.getDependencies().create("io.micronaut.aot:micronaut-aot-api:" + v)));
            c.getDependencies().addLater(aotExtension.getVersion().map(v -> project.getDependencies().create("io.micronaut.aot:micronaut-aot-std-optimizers:" + v)));
            c.getDependencies().addLater(aotExtension.getVersion().map(v -> project.getDependencies().create("io.micronaut.aot:micronaut-aot-cli:" + v)));
        });
        // User configurations
        Configuration aotPlugins = configurations.create("aotPlugins", c -> {
            c.setCanBeResolved(false);
            c.setCanBeConsumed(false);
        });
        Configuration aotApplication = configurations.create("aotApplication", c -> {
            c.setCanBeResolved(false);
            c.setCanBeConsumed(false);
        });
        Configuration aotApplicationClasspath = configurations.create(AOT_APPLICATION_CLASSPATH, c -> {
            configureAsRuntimeClasspath(providers, configurations, c);
            Configuration runtimeClasspath = configurations.findByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);
            runtimeClasspath.getExtendsFrom().forEach(c::extendsFrom);
            c.extendsFrom(aotApplication);
            c.getDependencies().add(project.getDependencies().create(project));
        });
        aotApplicationClasspath.extendsFrom(aotPlugins);
        return new Configurations(
                aotOptimizerRuntimeClasspath,
                aotApplication,
                aotApplicationClasspath
        );
    }

    private void configureAsRuntimeClasspath(ProviderFactory providers, ConfigurationContainer configurations, Configuration configuration) {
        configuration.setCanBeResolved(true);
        configuration.setCanBeConsumed(false);

        Configuration runtimeClasspath = configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);
        AttributeUtils.copyAttributes(providers, runtimeClasspath, configuration);
    }

    private static class JarExclusionSpec implements Action<FileCopyDetails> {
        private final Provider<RegularFile> filterFile;
        private final Set<String> prefixes;
        private final Logger logger;
        private Set<String> excludes;

        private JarExclusionSpec(Provider<RegularFile> filterFile,
                                 Set<String> prefixes,
                                 Logger logger) {
            this.filterFile = filterFile;
            this.prefixes = prefixes;
            this.logger = logger;
        }

        @Override
        public void execute(FileCopyDetails details) {
            if (excludes == null) {
                File resourceFilter = filterFile.get().getAsFile();
                try {
                    excludes = new HashSet<>();
                    Files.readAllLines(resourceFilter.toPath())
                            .stream()
                            .map(JarExclusionSpec::normalizePath)
                            .forEach(excludes::add);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                logger.debug("Excluded resources: {} ", excludes);
            }
            RelativePath relativePath = details.getRelativePath();
            if (excludes.contains(normalizePath(relativePath.getPathString())) || prefixes.stream().anyMatch(p -> normalizePath(relativePath.getPathString()).startsWith(p))) {
                details.exclude();
            }
        }

        private static String normalizePath(String path) {
            return path.replace('\\', '/');
        }
    }

    private record Configurations(
        Configuration aotOptimizerRuntimeClasspath,
        Configuration aotApplication,
        Configuration aotApplicationClasspath
    ) {
    }
}
