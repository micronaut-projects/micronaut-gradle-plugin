package io.micronaut.gradle.docker;

import io.micronaut.gradle.MicronautExtension;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.bundling.Jar;

import java.io.File;
import java.util.*;

import static org.gradle.api.plugins.JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME;

public class MicronautDockerPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        TaskContainer tasks = project.getTasks();
        MicronautExtension micronautExtension = project.getExtensions().getByType(MicronautExtension.class);
        File applicationLayout = new File(project.getBuildDir(), "layers");
        TaskProvider<Jar> runnerJar = tasks.register("runnerJar", Jar.class, jar -> {
            jar.dependsOn(tasks.findByName("classes"));
            jar.getArchiveClassifier().set("runner");
            SourceSetContainer sourceSets = project
                    .getExtensions().getByType(SourceSetContainer.class);

            SourceSet mainSourceSet = sourceSets
                    .getByName(SourceSet.MAIN_SOURCE_SET_NAME);

            FileCollection dirs = mainSourceSet.getOutput().getClassesDirs();

            jar.from(dirs);
        });

        //noinspection Convert2Lambda
        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(Project project) {
                Jar jar = runnerJar.get();
                jar.manifest(manifest -> {
                    Map<String, Object> attrs = new HashMap<>(2);
                    JavaApplication javaApplication = project.getExtensions().getByType(JavaApplication.class);
                    String mainClassName = javaApplication.getMainClassName();
                    Configuration runtimeClasspath = project.getConfigurations()
                            .getByName(RUNTIME_CLASSPATH_CONFIGURATION_NAME);

                    List<String> classpath = new ArrayList<>();
                    for (File file : runtimeClasspath) {
                        classpath.add("libs/" + file.getName());
                    }
                    classpath.add("resources/");

                    attrs.put("Main-Class", mainClassName);
                    attrs.put("Class-Path", String.join(" ", classpath));
                    manifest.attributes(attrs);
                });
            }
        });


        TaskProvider<Task> buildLayersTask = tasks.register("buildLayers", task -> {
            task.dependsOn(runnerJar);

            Configuration runtimeClasspath = project.getConfigurations()
                    .getByName(RUNTIME_CLASSPATH_CONFIGURATION_NAME);
            TaskOutputs jar = runnerJar.get().getOutputs();
            FileCollection jarFiles = jar.getFiles();
            SourceSetContainer sourceSets = project
                    .getExtensions().getByType(SourceSetContainer.class);

            SourceSet mainSourceSet = sourceSets
                    .getByName(SourceSet.MAIN_SOURCE_SET_NAME);

            Set<File> resourceDirs = mainSourceSet.getResources().getSrcDirs();

            TaskInputs inputs = task.getInputs();
            inputs.files(runtimeClasspath);
            inputs.files(jarFiles);
            inputs.files(resourceDirs);


            task.getOutputs().dir(applicationLayout);
            task.setGroup(BasePlugin.BUILD_GROUP);
            task.setDescription("Builds application layers for use in a Docker container");

            // NOTE: Has to be an anonymous inner class otherwise incremental build does not work
            // DO NOT REPLACE WITH LAMBDA
            //noinspection Convert2Lambda
            task.doLast(new Action<Task>() {
                @Override
                public void execute(Task t) {
                    project.copy(copy ->
                            copy.from(runtimeClasspath)
                                    .into(new File(applicationLayout, "libs"))
                    );
                    project.copy(copy ->
                            copy.from(jarFiles)
                                    .into(applicationLayout)
                                    .rename(s -> "application.jar")
                    );
                    project.copy(copy ->
                            copy.from(project.files(resourceDirs))
                                    .into(new File(applicationLayout, "resources"))
                    );
                }
            });
        });

        Task assemble = tasks.findByName("assemble");
        if (assemble != null) {
            assemble.dependsOn(buildLayersTask);
        }

        tasks.register("buildDockerImage", DockerBuildTask.class, task ->
            task.dependsOn(buildLayersTask)
        ).configure(task -> {
            task.setGroup(BasePlugin.BUILD_GROUP);
            task.setDescription("Builds a Docker Image");
            DockerSettings docker = micronautExtension.getDocker();
            String baseImage = docker.getFrom().getOrNull();
            if (baseImage != null) {
                task.getBaseImage().set(baseImage);
            }
            String tag = docker.getTag().getOrElse(project.getName());
            task.getTag().set(tag);
            int port = docker.getPort().get();
            task.getPort().set(port);

        });



    }
}
