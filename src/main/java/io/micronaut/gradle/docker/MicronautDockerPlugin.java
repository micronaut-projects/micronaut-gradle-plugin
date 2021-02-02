package io.micronaut.gradle.docker;

import com.bmuschko.gradle.docker.DockerExtension;
import com.bmuschko.gradle.docker.tasks.container.DockerCopyFileFromContainer;
import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer;
import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer;
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage;
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage;
import com.bmuschko.gradle.docker.tasks.image.Dockerfile;
import io.micronaut.gradle.MicronautApplicationPlugin;
import io.micronaut.gradle.MicronautRuntime;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.bundling.Jar;

import java.io.File;
import java.util.*;
import java.util.Optional;

import static org.gradle.api.plugins.JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME;

public class MicronautDockerPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        TaskContainer tasks = project.getTasks();
        ExtensionContainer extensions = project.getExtensions();
        extensions.create("docker", DockerExtension.class);
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

        // NOTE: Has to be an anonymous inner class otherwise incremental build does not work
        // DO NOT REPLACE WITH LAMBDA
        //noinspection Convert2Lambda
        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(Project project) {
                Jar jar = runnerJar.get();
                jar.manifest(manifest -> {
                    Map<String, Object> attrs = new HashMap<>(2);
                    JavaApplication javaApplication = project.getExtensions().getByType(JavaApplication.class);
                    String mainClassName = javaApplication.getMainClass().get();
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

            final File resourcesDir = mainSourceSet.getOutput().getResourcesDir();

            TaskInputs inputs = task.getInputs();
            inputs.files(runtimeClasspath);
            inputs.files(jarFiles);
            inputs.files(resourcesDir);


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
                    project.mkdir(new File(applicationLayout, "resources"));
                    project.copy(copy ->
                            copy.from(resourcesDir)
                                    .into(new File(applicationLayout, "resources"))
                    );
                }
            });
        });

        Task assemble = tasks.findByName("assemble");
        if (assemble != null) {
            assemble.dependsOn(buildLayersTask);
        }

        Optional<TaskProvider<MicronautDockerfile>> dockerFileTask = configureDockerBuild(project, tasks, buildLayersTask);
        TaskProvider<NativeImageDockerfile> nativeImageDockerFileTask = configureNativeDockerBuild(project, tasks, buildLayersTask);

        project.afterEvaluate(eval -> {
            nativeImageDockerFileTask.configure(NativeImageDockerfile::setupNativeImageTaskPostEvaluate);
            dockerFileTask.ifPresent(t -> t.configure(MicronautDockerfile::setupTaskPostEvaluate));
        });
    }

    private Optional<TaskProvider<MicronautDockerfile>> configureDockerBuild(Project project, TaskContainer tasks, TaskProvider<Task> buildLayersTask) {
        File f = project.file("Dockerfile");

        TaskProvider<? extends Dockerfile> dockerFileTask;
        if (f.exists()) {
            dockerFileTask = tasks.register("dockerfile", Dockerfile.class, task -> {
                        task.setGroup(BasePlugin.BUILD_GROUP);
                        task.setDescription("Builds a Docker File");
                        task.instructionsFromTemplate(f);
                    }
            );
        } else {
            dockerFileTask = tasks.register("dockerfile", MicronautDockerfile.class);
            dockerFileTask.configure(task -> {
                MicronautRuntime mr = MicronautApplicationPlugin.resolveRuntime(project);
                if (mr != MicronautRuntime.NONE) {
                    ((MicronautDockerfile) task).getBuildStrategy().set(mr.getBuildStrategy());
                }
                ((MicronautDockerfile) task).setupDockerfileInstructions();
            });
        }
        TaskProvider<DockerBuildImage> dockerBuildTask = tasks.register("dockerBuild", DockerBuildImage.class);
        dockerBuildTask.configure(task -> {
            task.dependsOn(buildLayersTask);
            task.setGroup(BasePlugin.BUILD_GROUP);
            task.setDescription("Builds a Docker Image");
            task.getInputDir().set(project.getProjectDir());
            if (f.exists()) {
                task.getDockerFile().set(f);
            } else {
                task.getDockerFile()
                        .convention(dockerFileTask.flatMap(Dockerfile::getDestFile));
            }
            task.getImages().set(Collections.singletonList(project.getName()));
        });


        TaskProvider<DockerPushImage> pushDockerImage = tasks.register("dockerPush", DockerPushImage.class);
        pushDockerImage.configure(task -> {
            task.dependsOn(dockerBuildTask);
            task.setGroup(BasePlugin.UPLOAD_GROUP);
            task.setDescription("Pushes a Docker Image");
            task.getImages().set(dockerBuildTask.flatMap(DockerBuildImage::getImages));
        });
        if (!f.exists()) {
            return Optional.of((TaskProvider<MicronautDockerfile>) dockerFileTask);
        }
        return Optional.empty();
    }

    private TaskProvider<NativeImageDockerfile> configureNativeDockerBuild(Project project, TaskContainer tasks, TaskProvider<Task> buildLayersTask) {
        File f = project.file("DockerfileNative");

        TaskProvider<NativeImageDockerfile> dockerFileTask;
        if (f.exists()) {
            dockerFileTask = tasks.register("dockerfileNative", NativeImageDockerfile.class, task -> {
                task.setGroup(BasePlugin.BUILD_GROUP);
                task.setDescription("Builds a Native Docker File");
                task.instructionsFromTemplate(f);
            });
        } else {
            dockerFileTask = tasks.register("dockerfileNative", NativeImageDockerfile.class);
            dockerFileTask.configure(task -> {
                MicronautRuntime mr = MicronautApplicationPlugin.resolveRuntime(project);
                if (mr != MicronautRuntime.NONE) {
                    task.getBuildStrategy().set(mr.getBuildStrategy());
                }
                task.setupDockerfileInstructions();
            });
        }
        TaskProvider<DockerBuildImage> dockerBuildTask = tasks.register("dockerBuildNative", DockerBuildImage.class);
        dockerBuildTask.configure(task -> {
            task.setGroup(BasePlugin.BUILD_GROUP);
            task.setDescription("Builds a Native Docker Image using GraalVM");
            task.getInputDir().set(project.getProjectDir());
            if (f.exists()) {
                task.getDockerFile().set(f);
            } else {
                task.getDockerFile()
                        .convention(dockerFileTask.flatMap(Dockerfile::getDestFile));
            }
            task.getImages().set(Collections.singletonList(project.getName()));
            task.dependsOn(buildLayersTask);
        });

        TaskProvider<DockerPushImage> pushDockerImage = tasks.register("dockerPushNative", DockerPushImage.class);
        pushDockerImage.configure(task -> {
            task.dependsOn(dockerBuildTask);
            task.setGroup(BasePlugin.UPLOAD_GROUP);
            task.setDescription("Pushes a Native Docker Image using GraalVM");
            task.getImages().set(dockerBuildTask.flatMap(DockerBuildImage::getImages));
        });

        project.afterEvaluate(p -> {
            MicronautRuntime mr = MicronautApplicationPlugin.resolveRuntime(p);
            if (mr == MicronautRuntime.LAMBDA) {
                TaskContainer taskContainer = p.getTasks();
                TaskProvider<DockerCreateContainer> createLambdaContainer = taskContainer.register("createLambdaContainer", DockerCreateContainer.class);
                createLambdaContainer.configure(task -> {
                    task.dependsOn(dockerBuildTask);
                    task.targetImageId(dockerBuildTask.flatMap(DockerBuildImage::getImageId));
                });
                TaskProvider<DockerCopyFileFromContainer> buildLambdaZip = taskContainer.register("buildNativeLambda", DockerCopyFileFromContainer.class);
                File lambdaZip = new File(project.getBuildDir(), "libs/" + project.getName() + "-" + project.getVersion() + "-lambda.zip");
                TaskProvider<DockerRemoveContainer> removeContainer = taskContainer.register("destroyLambdaContainer", DockerRemoveContainer.class);
                removeContainer.configure(task -> {
                    task.mustRunAfter(buildLambdaZip);
                    task.getContainerId().set(
                            createLambdaContainer.flatMap(DockerCreateContainer::getContainerId)
                    );
                });
                buildLambdaZip.configure(task -> {
                    task.dependsOn(createLambdaContainer);
                    task.getContainerId().set(
                            createLambdaContainer.flatMap(DockerCreateContainer::getContainerId)
                    );
                    task.getRemotePath().set("/function/function.zip");
                    task.getHostPath().set(lambdaZip.getAbsolutePath());
                    task.doLast(task1 -> System.out.println("AWS Lambda ZIP built: " + lambdaZip));
                    task.finalizedBy(removeContainer);
                });
            }

        });
        return dockerFileTask;
    }

}
