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
import io.micronaut.gradle.docker.tasks.BuildLayersTask;
import io.micronaut.gradle.graalvm.NativeImageTask;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;

import java.io.File;
import java.util.*;

import static org.gradle.api.plugins.JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME;

public class MicronautDockerPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        TaskContainer tasks = project.getTasks();
        ExtensionContainer extensions = project.getExtensions();
        extensions.create("docker", DockerExtension.class);
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


        tasks.create("internalDockerNativeImageTask", NativeImageTask.class, nativeImageTask -> {
            nativeImageTask.setEnabled(false);
        });
        TaskProvider<BuildLayersTask> buildLayersTask = tasks.register("buildLayers", BuildLayersTask.class, task -> {
            task.dependsOn(runnerJar);
            task.setGroup(BasePlugin.BUILD_GROUP);
            task.setDescription("Builds application layers for use in a Docker container");
            task.getLibsLayer().from(project.getConfigurations()
                    .getByName(RUNTIME_CLASSPATH_CONFIGURATION_NAME));
            task.getResourcesLayer().from(project.getExtensions().getByType(SourceSetContainer.class)
                    .getByName(SourceSet.MAIN_SOURCE_SET_NAME).getOutput().getResourcesDir());
            task.getAppLayer().from(runnerJar);
        });

        Task assemble = tasks.findByName("assemble");
        if (assemble != null) {
            assemble.dependsOn(buildLayersTask);
        }

        Optional<TaskProvider<MicronautDockerfile>> dockerFileTask = configureDockerBuild(project, tasks, buildLayersTask);
        TaskProvider<NativeImageDockerfile> nativeImageDockerFileTask = configureNativeDockerBuild(project, tasks, buildLayersTask);

        project.afterEvaluate(eval -> {
            Optional<DockerBuildStrategy> buildStrategy;
            MicronautRuntime mr = MicronautApplicationPlugin.resolveRuntime(project);
            if (mr != MicronautRuntime.NONE) {
                buildStrategy = Optional.of(mr.getBuildStrategy());
            }
            else {
                buildStrategy = Optional.empty();
            }
            nativeImageDockerFileTask.configure(it -> {
                buildStrategy.ifPresent(bs -> it.getBuildStrategy().set(buildStrategy.get()));
                it.setupNativeImageTaskPostEvaluate();
            });
            dockerFileTask.ifPresent(t -> t.configure(it -> {
                buildStrategy.ifPresent(bs -> it.getBuildStrategy().set(buildStrategy.get()));
                it.setupTaskPostEvaluate();
            }));
        });
    }

    private Optional<TaskProvider<MicronautDockerfile>> configureDockerBuild(Project project,
                                                                             TaskContainer tasks,
                                                                             TaskProvider<BuildLayersTask> buildLayersTask) {
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
                ((MicronautDockerfile) task).setupDockerfileInstructions();
            });
        }
        TaskProvider<DockerBuildImage> dockerBuildTask = tasks.register("dockerBuild", DockerBuildImage.class);
        dockerBuildTask.configure(task -> {
            task.dependsOn(buildLayersTask);
            task.setGroup(BasePlugin.BUILD_GROUP);
            task.setDescription("Builds a Docker Image");
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

    private TaskProvider<NativeImageDockerfile> configureNativeDockerBuild(Project project,
                                                                           TaskContainer tasks,
                                                                           TaskProvider<BuildLayersTask> buildLayersTask) {
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
            dockerFileTask.configure(NativeImageDockerfile::setupDockerfileInstructions);
        }
        TaskProvider<DockerBuildImage> dockerBuildTask = tasks.register("dockerBuildNative", DockerBuildImage.class);
        dockerBuildTask.configure(task -> {
            task.setGroup(BasePlugin.BUILD_GROUP);
            task.setDescription("Builds a Native Docker Image using GraalVM");
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
