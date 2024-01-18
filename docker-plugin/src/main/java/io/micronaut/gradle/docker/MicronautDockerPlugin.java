package io.micronaut.gradle.docker;

import com.bmuschko.gradle.docker.DockerRemoteApiPlugin;
import com.bmuschko.gradle.docker.tasks.container.DockerCopyFileFromContainer;
import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer;
import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer;
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage;
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage;
import com.bmuschko.gradle.docker.tasks.image.Dockerfile;
import io.micronaut.gradle.MicronautBasePlugin;
import io.micronaut.gradle.MicronautExtension;
import io.micronaut.gradle.MicronautRuntime;
import io.micronaut.gradle.PluginsHelper;
import io.micronaut.gradle.docker.model.DefaultMicronautDockerImage;
import io.micronaut.gradle.docker.model.LayerKind;
import io.micronaut.gradle.docker.model.MicronautDockerImage;
import io.micronaut.gradle.docker.model.RuntimeKind;
import io.micronaut.gradle.docker.tasks.BuildLayersTask;
import io.micronaut.gradle.docker.tasks.PrepareDockerContext;
import org.graalvm.buildtools.gradle.dsl.NativeImageOptions;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static io.micronaut.gradle.Strings.capitalize;
import static org.gradle.api.plugins.JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME;

public class MicronautDockerPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(DockerRemoteApiPlugin.class);
        project.getPluginManager().apply(MicronautBasePlugin.class);
        TaskContainer tasks = project.getTasks();
        ExtensionContainer extensions = project.getExtensions();
        MicronautExtension micronautExtension = extensions.getByType(MicronautExtension.class);
        NamedDomainObjectContainer<MicronautDockerImage> dockerImages = project.getObjects().domainObjectContainer(MicronautDockerImage.class, s -> project.getObjects().newInstance(DefaultMicronautDockerImage.class, s));
        micronautExtension.getExtensions().add("dockerImages", dockerImages);
        dockerImages.all(image -> createDockerImage(project, image));
        TaskProvider<Jar> runnerJar = createMainRunnerJar(project, tasks);
        dockerImages.create("main", image -> {
            createDependencyLayers(image, project.getConfigurations().getByName(RUNTIME_CLASSPATH_CONFIGURATION_NAME));
            image.addLayer(layer -> {
                layer.getLayerKind().set(LayerKind.APP);
                layer.getFiles().from(runnerJar);
            });
        });
    }

    public static void createDependencyLayers(MicronautDockerImage image, Configuration configuration) {
        var projectLibs = configuration.getIncoming()
            .artifactView(view -> {
                view.lenient(true);
                view.componentFilter(ProjectComponentIdentifier.class::isInstance);
            }).getFiles();
        var snapshotLibs = configuration.getIncoming()
            .artifactView(view -> {
                view.lenient(true);
                view.componentFilter(component -> {
                    if (component instanceof ModuleComponentIdentifier module) {
                        return module.getVersion().endsWith("-SNAPSHOT");
                    }
                    return !(component instanceof ProjectComponentIdentifier);
                });
            }).getFiles();
        var allOtherLibs = configuration.getIncoming()
            .artifactView(view -> {
                view.lenient(true);
                view.componentFilter(component -> {
                    if (component instanceof ModuleComponentIdentifier module) {
                        return !module.getVersion().endsWith("-SNAPSHOT");
                    }
                    return !(component instanceof ProjectComponentIdentifier);
                });
            }).getFiles();
        // First, all dependencies that are not snapshots
        image.addLayer(layer -> {
            layer.getLayerKind().set(LayerKind.LIBS);
            layer.getFiles().from(allOtherLibs);
        });
        // Then all snapshots
        image.addLayer(layer -> {
            layer.getLayerKind().set(LayerKind.SNAPSHOT_LIBS);
            layer.getFiles().from(snapshotLibs);
        });
        // Finally, all project dependencies
        image.addLayer(layer -> {
            layer.getLayerKind().set(LayerKind.PROJECT_LIBS);
            layer.getFiles().from(projectLibs);
        });
    }

    private static String simpleNameOf(String appendix, String context) {
        if ("main".equals(context)) {
            return appendix;
        }
        return context + "-" + appendix;
    }

    private static String adaptTaskName(String baseName, String context) {
        if ("main".equals(context)) {
            return baseName;
        }
        return context + capitalize(baseName);
    }

    private void createDockerImage(Project project, MicronautDockerImage imageSpec) {
        TaskContainer tasks = project.getTasks();
        String imageName = imageSpec.getName();
        project.getLogger().info("Creating docker tasks for image {}", imageName);
        TaskProvider<BuildLayersTask> buildLayersTask = tasks.register(adaptTaskName("buildLayers", imageName), BuildLayersTask.class, task -> {
            task.setGroup(BasePlugin.BUILD_GROUP);
            task.setDescription("Builds application layers for use in a Docker container (" + imageName + " image)");
            task.getLayers().set(imageSpec.findLayers(RuntimeKind.JIT));
            task.getOutputDir().convention(project.getLayout().getBuildDirectory().dir("docker/" + imageName + "/layers"));
        });


        tasks.configureEach(task -> {
            if (BasePlugin.ASSEMBLE_TASK_NAME.equals(task.getName())) {
                task.dependsOn(buildLayersTask);
            }
        });

        Optional<TaskProvider<MicronautDockerfile>> dockerFileTask = configureDockerBuild(project, tasks, buildLayersTask, imageName);
        project.getPlugins().withId("io.micronaut.graalvm", plugin -> {
            TaskProvider<BuildLayersTask> buildNativeLayersTask = tasks.register(adaptTaskName("buildNativeLayersTask", imageName), BuildLayersTask.class, task -> {
                task.setGroup(BasePlugin.BUILD_GROUP);
                task.setDescription("Builds application layers for use in a Docker container (" + imageName + " image)");
                task.getLayers().set(imageSpec.findLayers(RuntimeKind.NATIVE));
                task.getOutputDir().convention(project.getLayout().getBuildDirectory().dir("docker/native-" + imageName + "/layers"));
            });
            TaskProvider<NativeImageDockerfile> nativeImageDockerFileTask = configureNativeDockerBuild(project, tasks, buildNativeLayersTask, imageName);
            withBuildStrategy(project, buildStrategy -> nativeImageDockerFileTask.configure(it -> {
                buildStrategy.ifPresent(bs -> it.getBuildStrategy().set(buildStrategy.get()));
                it.setupNativeImageTaskPostEvaluate();
            }));
        });
        withBuildStrategy(project, buildStrategy -> dockerFileTask.ifPresent(t -> t.configure(it -> {
            buildStrategy.ifPresent(bs -> it.getBuildStrategy().set(buildStrategy.get()));
            it.setupTaskPostEvaluate();
        })));
    }

    private void withBuildStrategy(Project project, Consumer<? super Optional<DockerBuildStrategy>> action) {
        project.afterEvaluate(eval -> {
            Optional<DockerBuildStrategy> buildStrategy;
            MicronautRuntime mr = PluginsHelper.resolveRuntime(project);
            if (mr != MicronautRuntime.NONE) {
                buildStrategy = Optional.of(mr.getBuildStrategy());
            } else {
                buildStrategy = Optional.empty();
            }
            action.accept(buildStrategy);
        });
    }

    private TaskProvider<Jar> createMainRunnerJar(Project project, TaskContainer tasks) {
        return tasks.register("runnerJar", Jar.class, jar -> {
            jar.dependsOn(tasks.findByName("classes"));
            jar.getArchiveClassifier().set("runner");
            SourceSetContainer sourceSets = project
                .getExtensions().getByType(SourceSetContainer.class);

            SourceSet mainSourceSet = sourceSets
                .getByName(SourceSet.MAIN_SOURCE_SET_NAME);

            FileCollection dirs = mainSourceSet.getOutput().getClassesDirs();

            jar.from(dirs);
            jar.manifest(manifest -> {
                Map<String, Object> attrs = new HashMap<>(2);
                JavaApplication javaApplication = project.getExtensions().getByType(JavaApplication.class);
                attrs.put("Main-Class", javaApplication.getMainClass());
                attrs.put("Class-Path", project.getProviders().provider(() -> {
                    List<String> classpath = new ArrayList<>();
                    Configuration runtimeClasspath = project.getConfigurations()
                        .getByName(RUNTIME_CLASSPATH_CONFIGURATION_NAME);

                    classpath.add("resources/");
                    classpath.add("classes/");
                    for (File file : runtimeClasspath) {
                        classpath.add("libs/" + file.getName());
                    }
                    return String.join(" ", classpath);
                }));
                manifest.attributes(attrs);
            });
        });
    }

    private Optional<TaskProvider<MicronautDockerfile>> configureDockerBuild(Project project,
                                                                             TaskContainer tasks,
                                                                             TaskProvider<BuildLayersTask> buildLayersTask,
                                                                             String imageName) {
        File f = project.file(adaptTaskName("Dockerfile", imageName));

        TaskProvider<? extends Dockerfile> dockerFileTask;
        String dockerFileTaskName = adaptTaskName("dockerfile", imageName);
        Provider<RegularFile> targetDockerFile = project.getLayout().getBuildDirectory().file("docker/" + imageName + "/Dockerfile");
        if (f.exists()) {
            dockerFileTask = tasks.register(dockerFileTaskName, Dockerfile.class, task -> {
                task.setGroup(BasePlugin.BUILD_GROUP);
                task.setDescription("Builds a Docker File for image " + imageName);
                task.getDestFile().set(targetDockerFile);
                try {
                    task.instructionsFromTemplate(f);
                } catch (IOException e) {
                    throw new GradleException("Unable to configure docker task for image " + imageName, e);
                }
            });
        } else {
            dockerFileTask = tasks.register(dockerFileTaskName, MicronautDockerfile.class, task -> {
                task.setGroup(BasePlugin.BUILD_GROUP);
                task.setDescription("Builds a Docker File for image " + imageName);
                task.getDestFile().set(targetDockerFile);
                task.setupDockerfileInstructions();
                task.getLayers().convention(buildLayersTask.flatMap(BuildLayersTask::getLayers));
            });
        }
        TaskProvider<DockerBuildImage> dockerBuildTask = tasks.register(adaptTaskName("dockerBuild", imageName), DockerBuildImage.class, task -> {
            task.dependsOn(buildLayersTask);
            task.setGroup(BasePlugin.BUILD_GROUP);
            task.setDescription("Builds a Docker Image (image " + imageName + ")");
            task.getDockerFile().convention(dockerFileTask.flatMap(Dockerfile::getDestFile));
            task.getImages().set(Collections.singletonList(project.getName()));
            task.getInputDir().set(dockerFileTask.flatMap(Dockerfile::getDestDir));
        });

        TaskProvider<DockerPushImage> pushDockerImage = tasks.register(adaptTaskName("dockerPush", imageName), DockerPushImage.class, task -> {
            task.dependsOn(dockerBuildTask);
            task.setGroup("upload");
            task.setDescription("Pushes the " + imageName + " Docker Image");
            task.getImages().set(dockerBuildTask.flatMap(DockerBuildImage::getImages));
        });
        if (!f.exists()) {
            return Optional.of((TaskProvider<MicronautDockerfile>) dockerFileTask);
        }
        return Optional.empty();
    }

    private TaskProvider<NativeImageDockerfile> configureNativeDockerBuild(Project project,
                                                                           TaskContainer tasks,
                                                                           TaskProvider<BuildLayersTask> buildLayersTask,
                                                                           String imageName) {
        File f = project.file(adaptTaskName("DockerfileNative", imageName));

        TaskProvider<NativeImageDockerfile> dockerFileTask;
        String dockerfileNativeTaskName = adaptTaskName("dockerfileNative", imageName);
        Provider<RegularFile> targetDockerFile = project.getLayout().getBuildDirectory().file("docker/native-" + imageName + "/DockerfileNative");
        if (f.exists()) {
            dockerFileTask = tasks.register(dockerfileNativeTaskName, NativeImageDockerfile.class, task -> {
                task.setGroup(BasePlugin.BUILD_GROUP);
                task.setDescription("Builds a Native Docker File for image " + imageName);
                try {
                    task.instructionsFromTemplate(f);
                } catch (IOException e) {
                    throw new GradleException("Unable to configure docker task for image " + imageName, e);
                }
                task.getDestFile().set(targetDockerFile);
                task.getLayers().convention(buildLayersTask.flatMap(BuildLayersTask::getLayers));
            });
        } else {
            dockerFileTask = tasks.register(dockerfileNativeTaskName, NativeImageDockerfile.class, task -> {
                task.setGroup(BasePlugin.BUILD_GROUP);
                task.setDescription("Builds a Native Docker File for image " + imageName);
                task.getDestFile().set(targetDockerFile);
                task.getLayers().convention(buildLayersTask.flatMap(BuildLayersTask::getLayers));
            });
        }
        TaskProvider<PrepareDockerContext> prepareContext = tasks.register(adaptTaskName("dockerPrepareContext", imageName), PrepareDockerContext.class, context -> {
            // Because docker requires all files to be found in the build context we need to
            // copy the configuration file directories into the build context
            context.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("docker/native-" + imageName + "/config-dirs"));
            context.getInputDirectories().from(dockerFileTask.map(t -> t.getNativeImageOptions()
                .map(NativeImageOptions::getConfigurationFileDirectories).get() // drop dependency on building image
            ));
        });
        TaskProvider<DockerBuildImage> dockerBuildTask = tasks.register(adaptTaskName("dockerBuildNative", imageName), DockerBuildImage.class, task -> {
            task.setGroup(BasePlugin.BUILD_GROUP);
            task.setDescription("Builds a Native Docker Image using GraalVM (image " + imageName + ")");
            task.getInputs().files(prepareContext)
                    .withPropertyName("preparedDockerContext")
                    .withPathSensitivity(PathSensitivity.RELATIVE);
            task.getDockerFile().convention(dockerFileTask.flatMap(Dockerfile::getDestFile));
            task.getImages().set(Collections.singletonList(project.getName()));
            task.dependsOn(buildLayersTask);
            task.getInputDir().set(dockerFileTask.flatMap(Dockerfile::getDestDir));
        });

        TaskProvider<DockerPushImage> pushDockerImage = tasks.register(adaptTaskName("dockerPushNative", imageName), DockerPushImage.class);
        pushDockerImage.configure(task -> {
            task.dependsOn(dockerBuildTask);
            task.setGroup("upload");
            task.setDescription("Pushes a Native Docker Image using GraalVM (image " + imageName + ")");
            task.getImages().set(dockerBuildTask.flatMap(DockerBuildImage::getImages));
        });

        project.afterEvaluate(p -> {
            MicronautRuntime mr = PluginsHelper.resolveRuntime(p);
            if (mr.isLambdaProvided()) {
                TaskContainer taskContainer = p.getTasks();
                TaskProvider<DockerCreateContainer> createLambdaContainer = taskContainer.register(adaptTaskName("createLambdaContainer", imageName), DockerCreateContainer.class, task -> {
                    task.dependsOn(dockerBuildTask);
                    task.targetImageId(dockerBuildTask.flatMap(DockerBuildImage::getImageId));
                });
                TaskProvider<DockerCopyFileFromContainer> buildLambdaZip = taskContainer.register(adaptTaskName("buildNativeLambda", imageName), DockerCopyFileFromContainer.class);
                Provider<String> lambdaZip = project.getLayout()
                    .getBuildDirectory()
                    .dir("libs")
                    .map(dir -> dir.file(project.getName() + "-" + project.getVersion() + "-" + simpleNameOf("lambda", imageName) + ".zip").getAsFile().getAbsolutePath());
                TaskProvider<DockerRemoveContainer> removeContainer = taskContainer.register(adaptTaskName("destroyLambdaContainer", imageName), DockerRemoveContainer.class);
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
                    task.getHostPath().set(lambdaZip);
                    task.doLast(new Action<>() {
                        @Override
                        public void execute(Task task1) {
                            project.getLogger().lifecycle("AWS Lambda ZIP built: {}", lambdaZip.get());
                        }
                    });
                    task.finalizedBy(removeContainer);
                });
            }

        });
        return dockerFileTask;
    }
}
