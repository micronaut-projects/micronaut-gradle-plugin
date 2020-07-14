package io.micronaut.gradle.graalvm;

import io.micronaut.gradle.MicronautExtension;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;

import java.io.File;
import java.util.Set;

import static org.gradle.api.plugins.JavaPlugin.ANNOTATION_PROCESSOR_CONFIGURATION_NAME;

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
            if (extension.getEnableNativeImage().getOrElse(false)) {
                p.getDependencies().add(
                        ANNOTATION_PROCESSOR_CONFIGURATION_NAME,
                        "io.micronaut:micronaut-graal"
                );
            }
        });

        if (project.getPlugins().hasPlugin("application")) {
            TaskContainer tasks = project.getTasks();
            tasks.register("nativeImage", NativeImageTask.class, nativeImageTask -> {
                MicronautExtension extension = project.getExtensions().getByType(MicronautExtension.class);
                nativeImageTask.dependsOn(tasks.findByName("classes"));
                nativeImageTask.setGroup(BasePlugin.BUILD_GROUP);
                nativeImageTask.setDescription("Builds a GraalVM Native Image");
                Task assemble = tasks.findByName("assemble");
                if (assemble != null) {
                    assemble.dependsOn(nativeImageTask);
                }
                nativeImageTask.setEnabled(extension.getEnableNativeImage().getOrElse(false));
            });

            project.afterEvaluate(p -> p.getTasks().withType(NativeImageTask.class, nativeImageTask -> {
                JavaApplication javaApplication = p.getExtensions().getByType(JavaApplication.class);
                String mainClassName = javaApplication.getMainClassName();
                String imageName = p.getName();
                nativeImageTask.setMain(mainClassName);
                nativeImageTask.setImageName(imageName);
                FileCollection runtimeConfig = p
                        .getConfigurations()
                        .getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);
                SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
                SourceSet mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
                FileCollection outputDirs = mainSourceSet.getOutput().getClassesDirs();
                runtimeConfig = runtimeConfig.plus(outputDirs);
                Set<File> resourceDirs = mainSourceSet.getResources().getSrcDirs();
                runtimeConfig = runtimeConfig.plus(project.files(resourceDirs));
                nativeImageTask.setClasspath(runtimeConfig);
            }));
        }
    }
}
