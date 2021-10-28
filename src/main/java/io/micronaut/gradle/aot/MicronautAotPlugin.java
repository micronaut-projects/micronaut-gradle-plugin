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

import io.micronaut.gradle.MicronautApplicationPlugin;
import io.micronaut.gradle.MicronautExtension;
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
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.distribution.DistributionContainer;
import org.gradle.api.distribution.plugins.DistributionPlugin;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RelativePath;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.ApplicationPluginConvention;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.application.CreateStartScripts;
import org.gradle.api.tasks.bundling.Jar;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.codehaus.groovy.runtime.StringGroovyMethods.capitalize;

@SuppressWarnings("Convert2Lambda")
public abstract class MicronautAotPlugin implements Plugin<Project> {

    public static final String DEFAULT_AOT_VERSION = "1.0.0-SNAPSHOT";
    public static final String OPTIMIZED_BINARY_NAME = "optimized";
    public static final String OPTIMIZED_DIST_NAME = "optimized";
    public static final String MAIN_BINARY_NAME = "main";

    static final List<String> TYPES_TO_CHECK = Collections.unmodifiableList(Arrays.asList(
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
            "io.micronaut.core.async.subscriber.Completable"));

    static final List<String> SERVICE_TYPES = Collections.unmodifiableList(Arrays.asList(
            "io.micronaut.context.env.PropertySourceLoader",
            "io.micronaut.inject.BeanConfiguration",
            "io.micronaut.inject.BeanDefinitionReference",
            "io.micronaut.http.HttpRequestFactory",
            "io.micronaut.http.HttpResponseFactory",
            "io.micronaut.core.beans.BeanIntrospectionReference"
    ));

    @Inject
    protected abstract ArchiveOperations getArchiveOperations();

    @Override
    public void apply(Project project) {
        Configurations configurations = prepareConfigurations(project);
        project.getPlugins().withType(MicronautApplicationPlugin.class, p -> registerPrepareOptimizationsTasks(project, configurations));
    }

    private void registerPrepareOptimizationsTasks(Project project, Configurations configurations) {
        Configuration optimizerRuntimeClasspath = configurations.aotOptimizerRuntimeClasspath;
        Configuration applicationClasspath = configurations.aotApplicationClasspath;
        TaskContainer tasks = project.getTasks();
        AOTExtension aotExtension = project.getExtensions().getByType(MicronautExtension.class).getAot();
        TaskProvider<MicronautAotOptimizerTask> prepareJit = registerPrepareOptimizationTask(project, optimizerRuntimeClasspath, applicationClasspath, tasks, aotExtension, OptimizerIO.TargetRuntime.JIT);
        registerJavaExecOptimizedRun(project, tasks, prepareJit);

        TaskProvider<MicronautAotOptimizerTask> prepareNative = registerPrepareOptimizationTask(project, optimizerRuntimeClasspath, applicationClasspath, tasks, aotExtension, OptimizerIO.TargetRuntime.NATIVE);
        project.getPlugins().withType(NativeImagePlugin.class, p -> registerOptimizedBinary(project, prepareNative));
    }

    private void registerOptimizedDistribution(Project project,
                                               TaskProvider<Jar> optimizedJar) {
        DistributionContainer distributions = project.getExtensions().getByType(DistributionContainer.class);
        ApplicationPluginConvention appConvention = project.getConvention().getPlugin(ApplicationPluginConvention.class);
        ConfigurableFileCollection classpath = project.getObjects().fileCollection();
        classpath.from(optimizedJar);
        classpath.from(project.getConfigurations().getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME));
        TaskProvider<CreateStartScripts> startScripts = project.getTasks().register("create" + capitalize(OPTIMIZED_DIST_NAME) + "StartScripts", CreateStartScripts.class, task -> {
            JavaExec runTask = project.getTasks().named("run", JavaExec.class).get();
            task.setDescription("Creates OS specific scripts to run the AOT optimized application as a JVM application.");
            task.setClasspath(classpath);
            task.getMainClass().set(runTask.getMainClass());
            task.getConventionMapping().map("applicationName", appConvention::getApplicationName);
            task.getConventionMapping().map("outputDir", () -> new File(project.getBuildDir(), "optimizedScripts"));
            task.getConventionMapping().map("executableDir", appConvention::getExecutableDir);
            task.getConventionMapping().map("defaultJvmOpts", appConvention::getApplicationDefaultJvmArgs);
        });
        distributions.register(OPTIMIZED_DIST_NAME, dist -> dist.contents(contents -> {
            contents.into("bin", spec -> spec.from(startScripts));
            contents.into("lib", spec -> spec.from(classpath));
        }));
    }

    private void registerOptimizedBinary(Project project, TaskProvider<MicronautAotOptimizerTask> prepareNative) {
        GraalVMExtension graalVMExtension = project.getExtensions().getByType(GraalVMExtension.class);
        NamedDomainObjectContainer<NativeImageOptions> binaries = graalVMExtension.getBinaries();
        binaries.create(OPTIMIZED_BINARY_NAME, binary -> {
            NativeImageOptions main = binaries.getByName(MAIN_BINARY_NAME);
            binary.getMainClass().set(main.getMainClass());
            binary.getClasspath().from(main.getClasspath());
            binary.getClasspath().from(prepareNative.map(MicronautAotOptimizerTask::getGeneratedClassesDirectory));
        });
    }

    private TaskProvider<JavaExec> registerJavaExecOptimizedRun(Project project,
                                                                TaskContainer tasks,
                                                                TaskProvider<MicronautAotOptimizerTask> prepareJit) {
        TaskProvider<Jar> mainJar = tasks.named("jar", Jar.class);
        TaskProvider<MergeServiceFiles> mergeTask = tasks.register("mergeServiceFilesForOptimizedJar", MergeServiceFiles.class, task -> {
            task.getInputFiles().from(mainJar.map(jar -> getArchiveOperations().zipTree(jar.getArchiveFile().get().getAsFile())));
            task.getInputFiles().from(prepareJit.flatMap(MicronautAotOptimizerTask::getGeneratedClassesDirectory));
            task.getOutputDirectory().convention(project.getLayout().getBuildDirectory().dir("generated/aot/service-files"));
        });
        TaskProvider<Jar> jarTask = tasks.register("optimizedJar", Jar.class, jar -> {
            jar.getInputs().file(prepareJit.map(MicronautAotOptimizerTask::getGeneratedOutputResourceFilter));
            jar.getArchiveClassifier().convention("optimized");
            jar.from(mainJar.get().getSource(), spec -> spec.eachFile(
                    new JarExclusionSpec(
                            prepareJit.flatMap(MicronautAotOptimizerTask::getGeneratedOutputResourceFilter),
                            Collections.singleton("META-INF/services/"),
                            jar.getLogger()))
            );
            jar.from(prepareJit.map(MicronautAotOptimizerTask::getGeneratedClassesDirectory), spec -> spec.exclude("META-INF/services/**"));
            jar.from(mergeTask);
        });
        project.getPlugins().withType(DistributionPlugin.class, p -> registerOptimizedDistribution(project, jarTask));
        return tasks.register("optimizedRun", JavaExec.class, task -> {
            ProviderFactory providers = project.getProviders();
            JavaExec runTask = tasks.named("run", JavaExec.class).get();
            Provider<String> main = providers.provider(() -> runTask.getMainClass().get());
            task.getMainClass().convention(main);
            task.setClasspath(
                    project.files(jarTask, runTask.getClasspath().filter(f -> !mainJar.get().getArchiveFile().get().getAsFile().equals(f)))
            );
            task.doFirst(new Action<Task>() {
                @Override
                public void execute(Task t) {
                    if (task.getLogger().isDebugEnabled()) {
                        task.getLogger().debug(
                                "Running optimized entry point: " + task.getMainClass().get() +
                                        "\nClasspath:\n    " + task.getClasspath().getFiles()
                                        .stream()
                                        .map(File::getName)
                                        .collect(Collectors.joining("\n    "))
                        );
                    }
                }
            });
        });
    }

    private TaskProvider<MicronautAotOptimizerTask> registerPrepareOptimizationTask(Project project,
                                                                                    Configuration optimizerClasspath,
                                                                                    Configuration applicationClasspath,
                                                                                    TaskContainer tasks,
                                                                                    AOTExtension aotExtension,
                                                                                    MicronautAotOptimizerTask.TargetRuntime runtime) {
        String runtimeName = runtime.name().toLowerCase(Locale.US);
        String writeConfigTaskName = "write" + capitalize(runtimeName) + "AOTConfig";
        TaskProvider<MicronautAOTConfigWriterTask> configTask = tasks.register(writeConfigTaskName, MicronautAOTConfigWriterTask.class, task -> {
            task.getUserConfiguration().convention(aotExtension.getConfigFile());
            task.getAOTOptimizations().convention(aotExtension);
            task.getOutputFile().convention(project.getLayout().getBuildDirectory().file("generated/aot/" + runtimeName + ".properties"));
        });
        String prepareTaskName = "prepare" + capitalize(runtimeName) + "Optimizations";
        return tasks.register(prepareTaskName, MicronautAotOptimizerTask.class, task -> {
            task.getOptimizerClasspath().from(optimizerClasspath);
            task.getConfigurationFile().convention(configTask.flatMap(MicronautAOTConfigWriterTask::getOutputFile));
            ProviderFactory providers = project.getProviders();
            Provider<Directory> baseDir = project.getLayout().getBuildDirectory().dir("generated/aot/" + runtimeName);
            task.getOutputDirectory().convention(baseDir);
            task.getTargetRuntime().value(runtime).finalizeValue();
            TaskProvider<JavaExec> runTask = tasks.named("run", JavaExec.class);
            task.getTargetPackage().convention(providers.provider(() -> {
                String mainClass = runTask.get().getMainClass().get();
                return mainClass.substring(0, mainClass.lastIndexOf("."));
            }));
            task.getClasspath().from(applicationClasspath);
        });
    }

    @NotNull
    private Configurations prepareConfigurations(Project project) {
        ConfigurationContainer configurations = project.getConfigurations();
        // Internal configurations
        Configuration aotOptimizerRuntimeClasspath = configurations.create("aotOptimizerRuntimeClasspath", c -> {
            configureAsRuntimeClasspath(configurations, c);
            MicronautExtension micronautExtension = project.getExtensions().getByType(MicronautExtension.class);
            c.getDependencies().addLater(micronautExtension.getAot().getVersion().map(v -> project.getDependencies().create("io.micronaut.aot:micronaut-aot-api:" + v)));
            c.getDependencies().addLater(micronautExtension.getAot().getVersion().map(v -> project.getDependencies().create("io.micronaut.aot:micronaut-aot-cli:" + v)));
        });
        // User configurations
        Configuration aotApplication = configurations.create("aotApplication", c -> {
            c.setCanBeResolved(false);
            c.setCanBeConsumed(false);
        });
        Configuration aotApplicationClasspath = configurations.create("aotApplicationClasspath", c -> {
            configureAsRuntimeClasspath(configurations, c);
            Configuration runtimeClasspath = configurations.findByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);
            runtimeClasspath.getExtendsFrom().forEach(c::extendsFrom);
            c.extendsFrom(aotApplication);
            c.getDependencies().add(project.getDependencies().create(project));
        });
        return new Configurations(
                aotOptimizerRuntimeClasspath,
                aotApplication,
                aotApplicationClasspath
        );
    }

    private void configureAsRuntimeClasspath(ConfigurationContainer configurations, Configuration configuration) {
        configuration.setCanBeResolved(true);
        configuration.setCanBeConsumed(false);

        Configuration runtimeClasspath = configurations.findByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);
        configuration.attributes(attrs -> {
            AttributeContainer baseAttributes = runtimeClasspath.getAttributes();
            for (Attribute<?> attribute : baseAttributes.keySet()) {
                Attribute<Object> attr = (Attribute<Object>) attribute;
                Object value = baseAttributes.getAttribute(attr);
                attrs.attribute(attr, value);
            }
        });
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

    private static final class Configurations {
        private final Configuration aotOptimizerRuntimeClasspath;
        private final Configuration aotApplication;
        private final Configuration aotApplicationClasspath;

        private Configurations(Configuration aotOptimizerRuntimeClasspath, Configuration aotApplication, Configuration aotApplicationClasspath) {
            this.aotOptimizerRuntimeClasspath = aotOptimizerRuntimeClasspath;
            this.aotApplication = aotApplication;
            this.aotApplicationClasspath = aotApplicationClasspath;
        }
    }
}
