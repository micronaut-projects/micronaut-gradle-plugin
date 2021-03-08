package io.micronaut.gradle.graalvm;

import io.micronaut.gradle.MicronautApplicationPlugin;
import io.micronaut.gradle.MicronautExtension;
import io.micronaut.gradle.MicronautRuntime;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * Support for building GraalVM native images.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class MicronautGraalPlugin implements Plugin<Project> {
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
                    nativeImageTask.setMain("io.micronaut.function.aws.runtime.MicronautLambdaRuntime");
                }
                nativeImageTask.dependsOn(tasks.findByName("classes"));
                nativeImageTask.setGroup(BasePlugin.BUILD_GROUP);
                nativeImageTask.setDescription("Builds a GraalVM Native Image");
            });

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
}
