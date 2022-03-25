package io.micronaut.gradle.graalvm;

import io.micronaut.gradle.MicronautExtension;
import org.graalvm.buildtools.gradle.NativeImagePlugin;
import org.graalvm.buildtools.gradle.dsl.GraalVMExtension;
import org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.testing.Test;
import org.gradle.language.jvm.tasks.ProcessResources;
import org.gradle.process.CommandLineArgumentProvider;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Support for building GraalVM native images.
 *
 * @author graemerocher
 * @author Iván López
 * @since 1.0.0
 */
public class MicronautGraalPlugin implements Plugin<Project> {

    private static final Set<String> SOURCE_SETS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("main", "test")));

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(NativeImagePlugin.class);
        workaroundForResourcesDirectoryMissing(project);
        project.getPluginManager().withPlugin("io.micronaut.minimal.library", plugin -> {
            MicronautExtension extension = project.getExtensions().findByType(MicronautExtension.class);
            configureAnnotationProcessing(project, extension);
        });
        project.getPluginManager().withPlugin("io.micronaut.minimal.application", plugin -> {
            MicronautExtension extension = project.getExtensions().findByType(MicronautExtension.class);
            configureAnnotationProcessing(project, extension);
        });
        GraalVMExtension graal = project.getExtensions().findByType(GraalVMExtension.class);
        graal.getBinaries().configureEach(options ->
                options.resources(rsrc -> rsrc.autodetection(inf -> {
                    inf.getEnabled().convention(true);
                    inf.getIgnoreExistingResourcesConfigFile().convention(true);
                    inf.getRestrictToProjectDependencies().convention(true);
                }))
        );
        project.getPluginManager().withPlugin("application", plugin -> {
            TaskContainer tasks = project.getTasks();

            // We use `afterEvaluate` here in order to preserve laziness of task configuration
            // and because there is no API to allow reacting to registration of tasks.
            Set<String> alreadyRegisteredTaskNames = new HashSet<>();
            project.afterEvaluate(p -> tasks.withType(Test.class).getCollectionSchema().getElements().forEach(element -> {
                String testName = element.getName();
                registerTestAgainstNativeImageTask(alreadyRegisteredTaskNames, tasks, testName);
            }));
        });
    }

    private void workaroundForResourcesDirectoryMissing(Project project) {
        project.getPluginManager().withPlugin("java", plugin ->
                project.afterEvaluate(unused -> {
                    // Workaround for https://github.com/graalvm/native-build-tools/issues/175
                    // and https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/306
                    project.getTasks().withType(ProcessResources.class, task -> {
                        // yes we do this at config time, because otherwise the workaround
                        // simply doesn't work because there would be no inputs so that
                        // task would never be executed. So yes, this is incorrect because
                        // the destination dir _could_ be changed after this is executed
                        File destinationDir = task.getDestinationDir();
                        if (destinationDir != null) {
                            destinationDir.mkdirs();
                        }
                    });
                })
        );
    }

    /**
     * For each `Test` task, we register a new `testNativeImage` task which tests _against_ the native image
     * server. Note that this is different from the `nativeTest` task that the GraalVM Gradle plugin provides,
     * as the latter executes all tests _within_ the native image.
     */
    private void registerTestAgainstNativeImageTask(Set<String> alreadyRegistered, TaskContainer tasks, String testName) {
        if (alreadyRegistered.add(testName)) {
            tasks.register(testName + "NativeImage", Test.class, nativeImageTestTask -> {
                Test testTask = (Test) tasks.getByName(testName);
                nativeImageTestTask.setClasspath(testTask.getClasspath());
                nativeImageTestTask.getJavaLauncher().set(testTask.getJavaLauncher());
                BuildNativeImageTask nativeBuild = (BuildNativeImageTask) tasks.findByName("nativeCompile");
                nativeImageTestTask.setForkEvery(testTask.getForkEvery());
                nativeImageTestTask.setTestClassesDirs(testTask.getTestClassesDirs());
                nativeImageTestTask.getJvmArgumentProviders().add(new CommandLineArgumentProvider() {
                    @InputFile
                    @PathSensitive(PathSensitivity.RELATIVE)
                    Provider<RegularFile> getInputFile() {
                        return nativeBuild.getOutputFile();
                    }

                    @Override
                    public Iterable<String> asArguments() {
                        return Collections.singleton(
                                "-Dmicronaut.test.server.executable=" + getInputFile().get().getAsFile().getAbsolutePath()
                        );
                    }
                });
                nativeImageTestTask.setDescription("Runs tests against a native image build of the server. Requires the server to allow the port to configurable with 'micronaut.server.port'.");
            });
        }
    }

    private static void configureAnnotationProcessing(Project project, MicronautExtension extension) {
        SourceSetContainer sourceSets = project
                .getConvention()
                .getPlugin(JavaPluginConvention.class)
                .getSourceSets();
        project.afterEvaluate(unused -> {
            ListProperty<SourceSet> sets = extension.getProcessing().getAdditionalSourceSets();
            if (sets.isPresent()) {
                addGraalVMAnnotationProcessorDependency(project, sets.get());
            }
        });

        addGraalVMAnnotationProcessorDependency(project,
                sourceSets.stream()
                        .filter(sourceSet -> SOURCE_SETS.contains(sourceSet.getName()))
                        .collect(Collectors.toList())
        );
    }

    private static void addGraalVMAnnotationProcessorDependency(Project project, Iterable<SourceSet> sourceSets) {
        for (SourceSet sourceSet : sourceSets) {
            project.getDependencies().add(
                    sourceSet.getAnnotationProcessorConfigurationName(),
                    "io.micronaut:micronaut-graal"
            );
        }
    }
}
