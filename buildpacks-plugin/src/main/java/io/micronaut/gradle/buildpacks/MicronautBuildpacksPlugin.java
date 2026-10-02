package io.micronaut.gradle.buildpacks;

import io.micronaut.gradle.MicronautBasePlugin;
import io.micronaut.gradle.MicronautExtension;
import io.micronaut.gradle.buildpacks.tasks.BuildpackImageTask;
import io.micronaut.gradle.buildpacks.tasks.PrepareBuildpackApplicationTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.gradle.api.plugins.JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME;

/**
 * Adds Cloud Native Buildpacks image tasks for Micronaut applications.
 */
public class MicronautBuildpacksPlugin implements Plugin<Project> {
    public static final String BUILD_BUILDPACK_IMAGE_TASK_NAME = "buildBuildpackImage";
    public static final String PUBLISH_BUILDPACK_IMAGE_TASK_NAME = "publishBuildpackImage";
    public static final String BUILD_NATIVE_BUILDPACK_IMAGE_TASK_NAME = "buildNativeBuildpackImage";
    public static final String PUBLISH_NATIVE_BUILDPACK_IMAGE_TASK_NAME = "publishNativeBuildpackImage";
    private static final String PREPARE_BUILDPACK_APPLICATION_TASK_NAME = "prepareBuildpackApplication";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(MicronautBasePlugin.class);
        project.getPluginManager().withPlugin("application", unused -> configure(project));
    }

    private void configure(Project project) {
        MicronautExtension micronautExtension = project.getExtensions().getByType(MicronautExtension.class);
        BuildpacksExtension extension = micronautExtension.getExtensions().create("buildpacks", BuildpacksExtension.class);
        extension.getImageName().convention(project.provider(() -> project.getName() + ":latest"));
        extension.getTags().convention(List.of());
        extension.getBuilder().convention("paketobuildpacks/builder-jammy-base");
        extension.getBuildpacks().convention(List.of());
        extension.getEnvironment().convention(new HashMap<>());
        extension.getPublish().convention(false);
        extension.getPackExecutable().convention("pack");
        extension.getTrustBuilder().convention(false);

        TaskProvider<Jar> runnerJar = createRunnerJar(project);
        TaskProvider<PrepareBuildpackApplicationTask> prepareApplication = project.getTasks().register(PREPARE_BUILDPACK_APPLICATION_TASK_NAME, PrepareBuildpackApplicationTask.class, task -> {
            task.setGroup(BasePlugin.BUILD_GROUP);
            task.setDescription("Prepares the Micronaut application for Cloud Native Buildpacks");
            task.getApplicationJar().set(runnerJar.flatMap(Jar::getArchiveFile));
            task.getRuntimeClasspath().from(project.getConfigurations().getByName(RUNTIME_CLASSPATH_CONFIGURATION_NAME));
            task.getResources().from(project.getProviders().provider(() -> {
                SourceSet mainSourceSet = project.getExtensions().getByType(SourceSetContainer.class).getByName(SourceSet.MAIN_SOURCE_SET_NAME);
                File resourcesDir = mainSourceSet.getOutput().getResourcesDir();
                if (resourcesDir == null) {
                    return List.of();
                }
                return List.of(resourcesDir);
            }));
            task.getOutputDirectory().convention(project.getLayout().getBuildDirectory().dir("buildpacks/main/application"));
        });

        registerBuildpackImageTask(project, BUILD_BUILDPACK_IMAGE_TASK_NAME, "Builds a Cloud Native Buildpacks image", extension, prepareApplication, false, false);
        registerBuildpackImageTask(project, PUBLISH_BUILDPACK_IMAGE_TASK_NAME, "Builds and publishes a Cloud Native Buildpacks image", extension, prepareApplication, true, false);
        registerBuildpackImageTask(project, BUILD_NATIVE_BUILDPACK_IMAGE_TASK_NAME, "Builds a native Cloud Native Buildpacks image", extension, prepareApplication, false, true);
        registerBuildpackImageTask(project, PUBLISH_NATIVE_BUILDPACK_IMAGE_TASK_NAME, "Builds and publishes a native Cloud Native Buildpacks image", extension, prepareApplication, true, true);
    }

    private TaskProvider<Jar> createRunnerJar(Project project) {
        return project.getTasks().register("buildpackRunnerJar", Jar.class, jar -> {
            jar.dependsOn(project.getTasks().named("classes"));
            jar.getArchiveClassifier().set("buildpack-runner");
            SourceSet mainSourceSet = project.getExtensions().getByType(SourceSetContainer.class).getByName(SourceSet.MAIN_SOURCE_SET_NAME);
            FileCollection classesDirs = mainSourceSet.getOutput().getClassesDirs();
            jar.from(classesDirs);
            jar.manifest(manifest -> {
                var attributes = new HashMap<String, Object>(2);
                JavaApplication javaApplication = project.getExtensions().getByType(JavaApplication.class);
                attributes.put("Main-Class", javaApplication.getMainClass());
                attributes.put("Class-Path", project.getProviders().provider(() -> {
                    var classpath = new ArrayList<String>();
                    classpath.add("resources/");
                    for (File file : project.getConfigurations().getByName(RUNTIME_CLASSPATH_CONFIGURATION_NAME)) {
                        classpath.add("libs/" + file.getName());
                    }
                    return String.join(" ", classpath);
                }));
                manifest.attributes(attributes);
            });
        });
    }

    private void registerBuildpackImageTask(Project project,
                                            String taskName,
                                            String description,
                                            BuildpacksExtension extension,
                                            TaskProvider<PrepareBuildpackApplicationTask> prepareApplication,
                                            boolean publish,
                                            boolean nativeImage) {
        project.getTasks().register(taskName, BuildpackImageTask.class, task -> {
            task.dependsOn(prepareApplication);
            task.setGroup(publish ? "upload" : BasePlugin.BUILD_GROUP);
            task.setDescription(description);
            task.getApplicationDirectory().set(prepareApplication.flatMap(PrepareBuildpackApplicationTask::getOutputDirectory));
            task.getImageName().convention(extension.getImageName());
            task.getTags().convention(extension.getTags());
            task.getBuilder().convention(extension.getBuilder());
            task.getRunImage().convention(extension.getRunImage());
            task.getBuildpacks().convention(extension.getBuildpacks());
            task.getEnvironment().putAll(extension.getEnvironment());
            task.getPublish().convention(project.provider(() -> publish || extension.getPublish().get()));
            task.getPackExecutable().convention(extension.getPackExecutable());
            task.getPullPolicy().convention(extension.getPullPolicy());
            task.getNetwork().convention(extension.getNetwork());
            task.getDockerHost().convention(extension.getDockerHost());
            task.getDockerConfigDirectory().convention(extension.getDockerConfigDirectory());
            task.getTrustBuilder().convention(extension.getTrustBuilder());
            if (nativeImage) {
                task.getEnvironment().put("BP_NATIVE_IMAGE", extension.getEnvironment().map(environment -> environment.getOrDefault("BP_NATIVE_IMAGE", "true")));
            }
        });
    }
}
