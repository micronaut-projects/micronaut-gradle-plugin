package io.micronaut.gradle.graalvm;

import io.micronaut.gradle.MicronautApplicationPlugin;
import io.micronaut.gradle.MicronautExtension;
import io.micronaut.gradle.MicronautRuntime;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.tasks.Jar;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Support for building GraalVM native images.
 *
 * @author graemerocher
 * @author Iván López
 * @since 1.0.0
 */
public class MicronautGraalPlugin implements Plugin<Project> {

    private static final List<String> DEPENDENT_CONFIGURATIONS = Arrays.asList(JavaPlugin.API_CONFIGURATION_NAME, JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME, JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME);

    @Override
    public void apply(Project project) {
        project.afterEvaluate(p -> {
            MicronautExtension extension = p.getExtensions().getByType(MicronautExtension.class);
            if (extension.getEnableNativeImage().getOrElse(true)) {
                p.getGradle().getTaskGraph().whenReady(taskGraph -> {
                    TaskContainer tasks = p.getTasks();
                    final Task nativeImage = tasks.findByName("nativeImage");
                    final Task dockerfileNative = tasks.findByName("dockerfileNative");
                    boolean addGraalProcessor = nativeImage != null && taskGraph.hasTask(nativeImage) || (dockerfileNative != null && taskGraph.hasTask(dockerfileNative));
                    if (addGraalProcessor) {
                        SourceSetContainer sourceSets = p.getConvention().getPlugin(JavaPluginConvention.class)
                                .getSourceSets();
                        for (String sourceSetName : Arrays.asList("main", "test")) {
                            SourceSet sourceSet = sourceSets.findByName(sourceSetName);
                            if (sourceSet != null) {
                                p.getDependencies().add(
                                        sourceSet.getAnnotationProcessorConfigurationName(),
                                        "io.micronaut:micronaut-graal"
                                );
                            }
                        }
                        ListProperty<SourceSet> additionalSourceSets = extension.getProcessing().getAdditionalSourceSets();
                        if (additionalSourceSets.isPresent()) {
                            List<SourceSet> sourceSetList = additionalSourceSets.get();
                            for (SourceSet sourceSet : sourceSetList) {
                                p.getDependencies().add(
                                        sourceSet.getAnnotationProcessorConfigurationName(),
                                        "io.micronaut:micronaut-graal"
                                );
                            }
                        }
                    }
                });


            }
        });

        if (project.getPlugins().hasPlugin("application")) {
            TaskContainer tasks = project.getTasks();
            TaskProvider<NativeImageTask> nit = tasks.register("nativeImage", NativeImageTask.class, nativeImageTask -> {
                MicronautRuntime mr = MicronautApplicationPlugin.resolveRuntime(project);
                if (mr == MicronautRuntime.LAMBDA) {
                    DependencySet implementation = project.getConfigurations().getByName("implementation").getDependencies();
                    boolean isAwsApp = implementation.stream()
                            .noneMatch(dependency -> Objects.equals(dependency.getGroup(), "io.micronaut.aws") && dependency.getName().equals("micronaut-function-aws"));

                    if (isAwsApp) {
                        nativeImageTask.setMain("io.micronaut.function.aws.runtime.MicronautLambdaRuntime");
                    }
                }
                nativeImageTask.dependsOn(tasks.findByName("classes"));
                nativeImageTask.setGroup(BasePlugin.BUILD_GROUP);
                nativeImageTask.setDescription("Builds a GraalVM Native Image");
            });

            project.afterEvaluate(p -> p
                    .getConfigurations()
                    .configureEach(configuration -> {
                        if (DEPENDENT_CONFIGURATIONS.contains(configuration.getName())) {
                            final DependencySet dependencies = configuration.getDependencies();
                            for (Dependency dependency : dependencies) {
                                if (dependency instanceof ProjectDependency) {
                                    final Project otherProject = ((ProjectDependency) dependency).getDependencyProject();
                                    otherProject.getTasks().withType(Jar.class, jar -> {
                                        if (jar.getName().equals("jar")) {
                                            nit.configure(nativeImageTask -> nativeImageTask.dependsOn(jar));
                                        }
                                    });
                                }
                            }
                        }
                    }));

            tasks.withType(Test.class, (test ->
                tasks.register(test.getName() + "NativeImage", nativeImageTestTask -> {
                    nativeImageTestTask.doLast((t) -> {
                        NativeImageTask nativeImage = nit.get();
                        File file = nativeImage.getNativeImageOutput();
                        test.systemProperty("micronaut.test.server.executable", file.getAbsolutePath());
                    });
                    boolean enabled = test.isEnabled() && GraalUtil.isGraalJVM();
                    nativeImageTestTask.onlyIf(task -> {
                        boolean isGraal = GraalUtil.isGraalJVM();
                        if (!isGraal) {
                            project.getLogger().log(LogLevel.INFO, "Skipping testNativeImage because the configured JDK is not a GraalVM JDK");
                        }
                        return isGraal;
                    });
                    if (enabled) {
                        nativeImageTestTask.dependsOn(nit);
                        test.mustRunAfter(nativeImageTestTask);
                        nativeImageTestTask.finalizedBy(test);
                    }
                    nativeImageTestTask.setDescription("Runs tests against a native image build of the server. Requires the server to allow the port to configurable with 'micronaut.server.port'.");
            })));

            TaskProvider<GenerateResourceConfigFile> generateResourceConfig = configureResourcesFileGeneration(project, tasks);
            tasks.withType(NativeImageTask.class).configureEach(nativeImage -> {
                // This isn't great. Ideally the configuration file directories should be an input
                // of the native image task directly, not something we patch
                DirectoryProperty outputDirectory = generateResourceConfig.get().getOutputDirectory();
                nativeImage.getInputs().files(generateResourceConfig);
                nativeImage.args("-H:ConfigurationFileDirectories="+outputDirectory.get().getAsFile().getAbsolutePath());
            });

            project.afterEvaluate(p -> p.getTasks().withType(NativeImageTask.class, nativeImageTask -> {
                if (!nativeImageTask.getName().equals("internalDockerNativeImageTask")) {
                    MicronautExtension extension = project.getExtensions().getByType(MicronautExtension.class);
                    nativeImageTask.setEnabled(extension.getEnableNativeImage().getOrElse(false));
                    JavaApplication javaApplication = p.getExtensions().getByType(JavaApplication.class);
                    String mainClassName = javaApplication.getMainClass().getOrNull();
                    String imageName = p.getName();
                    if (mainClassName != null && !nativeImageTask.getMain().isPresent()) {
                        nativeImageTask.setMain(mainClassName);
                    }
                    if (!nativeImageTask.getImageName().isPresent()) {
                        nativeImageTask.setImageName(imageName);
                    }
                }
            }));
        }
    }

    private TaskProvider<GenerateResourceConfigFile> configureResourcesFileGeneration(Project project, TaskContainer tasks) {
        return tasks.register("generateResourceConfigFile", GenerateResourceConfigFile.class, generator -> {
            SourceSetContainer sourceSets = project.getConvention()
                    .getPlugin(JavaPluginConvention.class)
                    .getSourceSets();
            SourceSet sourceSet = sourceSets.findByName(SourceSet.MAIN_SOURCE_SET_NAME);
            if (sourceSet != null) {
                generator.getResourceDirectories().from(sourceSet.getResources().getSourceDirectories());
                generator.getMixedContentsDirectories().from(sourceSet.getOutput().getClassesDirs());
            }
        });
    }
}
