package io.micronaut.gradle.crac;

import com.bmuschko.gradle.docker.tasks.container.*;
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage;
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage;
import com.bmuschko.gradle.docker.tasks.image.Dockerfile;
import io.micronaut.gradle.MicronautBasePlugin;
import io.micronaut.gradle.MicronautExtension;
import io.micronaut.gradle.MicronautRuntime;
import io.micronaut.gradle.PluginsHelper;
import io.micronaut.gradle.crac.tasks.CheckpointScriptTask;
import io.micronaut.gradle.docker.DockerBuildStrategy;
import io.micronaut.gradle.docker.model.MicronautDockerImage;
import io.micronaut.gradle.docker.tasks.BuildLayersTask;
import org.gradle.api.GradleException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.PluginManager;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Consumer;

import static io.micronaut.gradle.Strings.capitalize;

public class MicronautCRaCPlugin implements Plugin<Project> {

    public static final String CRAC_DEFAULT_BASE_IMAGE = "ubuntu:18.04";
    private static final String CRAC_TASK_GROUP = "CRaC";
    public static final String CRAC_CHECKPOINT = "crac_checkpoint";
    public static final String BUILD_DOCKER_DIRECTORY = "docker/";

    @Override
    public void apply(Project project) {
        PluginManager pluginManager = project.getPluginManager();
        pluginManager.apply(MicronautBasePlugin.class);
        configurePlugin(project);
    }

    private void configurePlugin(Project project) {
        ExtensionContainer extensions = project.getExtensions();
        MicronautExtension micronautExtension = extensions.getByType(MicronautExtension.class);
        CRaCConfiguration configuration = createCRaCConfiguration(project);
        NamedDomainObjectContainer<MicronautDockerImage> dockerImages = (NamedDomainObjectContainer<MicronautDockerImage>) micronautExtension.getExtensions().findByName("dockerImages");
        createCheckpointDockerImage(project, dockerImages.findByName("main"), configuration);
    }

    private CRaCConfiguration createCRaCConfiguration(Project project) {
        MicronautExtension micronautExtension = PluginsHelper.findMicronautExtension(project);
        CRaCConfiguration crac = micronautExtension.getExtensions().create("crac", CRaCConfiguration.class);
        crac.getEnabled().convention(true);
        crac.getImageName().convention("Woo");
        crac.getBaseImage().convention(CRAC_DEFAULT_BASE_IMAGE);
        return crac;
    }

    private static String adaptTaskName(String baseName, String context) {
        if ("main".equals(context)) {
            return baseName;
        }
        return context + capitalize(baseName);
    }

    private void createCheckpointDockerImage(Project project, MicronautDockerImage imageSpec, CRaCConfiguration configuration) {
        TaskContainer tasks = project.getTasks();
        String imageName = imageSpec.getName();
        project.getLogger().info("Creating checkpoint docker tasks for image {}", imageName);
        TaskProvider<CheckpointScriptTask> scriptTask = tasks.register(adaptTaskName("checkpointScripts", imageName), CheckpointScriptTask.class, task -> {
            task.setGroup(CRAC_TASK_GROUP);
            task.setDescription("Copies the scripts required for use in the CRaC checkpoint container (" + imageName + " image)");
            task.getCheckpointFile().set(configuration.getCheckpointScript());
            task.getWarmupFile().set(configuration.getWarmupScript());
            task.getOutputDir().convention(project.getLayout().getBuildDirectory().dir(BUILD_DOCKER_DIRECTORY + imageName + "/checkpoint"));
        });
        TaskProvider<BuildLayersTask> buildLayersTask = tasks.named("buildLayers", BuildLayersTask.class);
        CheckpointTasksOfNote checkpointDockerBuild = configureCheckpointDockerBuild(project, tasks, scriptTask, buildLayersTask, configuration, imageName);
        Optional<TaskProvider<CRaCFinalDockerfile>> finalDockerBuild = configureFinalDockerBuild(project, tasks, scriptTask, buildLayersTask, checkpointDockerBuild.start, configuration, imageName);
        withBuildStrategy(project, buildStrategy -> {
            checkpointDockerBuild.getCheckpointDockerBuild().ifPresent(t -> t.configure(it -> {
                buildStrategy.ifPresent(bs -> it.getBuildStrategy().set(buildStrategy.get()));
                it.setupTaskPostEvaluate();
            }));
            finalDockerBuild.ifPresent(t -> t.configure(it -> {
                buildStrategy.ifPresent(bs -> it.getBuildStrategy().set(buildStrategy.get()));
                it.setupTaskPostEvaluate();
            }));
        });
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

    private CheckpointTasksOfNote configureCheckpointDockerBuild(Project project,
                                                                 TaskContainer tasks,
                                                                 TaskProvider<CheckpointScriptTask> scriptTask,
                                                                 TaskProvider<BuildLayersTask> buildLayersTask,
                                                                 CRaCConfiguration configuration,
                                                                 String imageName) {
        File f = project.file(adaptTaskName("DockerfileCracCheckpoint", imageName));
        String dockerFileTaskName = adaptTaskName("checkpointDockerfile", imageName);
        Provider<RegularFile> targetCheckpointDockerFile = project.getLayout().getBuildDirectory().file(BUILD_DOCKER_DIRECTORY + imageName + "/Dockerfile.CRaCCheckpoint");
        TaskProvider<CRaCCheckpointDockerfile> dockerFileTask = tasks.register(dockerFileTaskName, CRaCCheckpointDockerfile.class, task -> {
            task.setGroup(CRAC_TASK_GROUP);
            task.setDescription("Builds a Checkpoint Docker File for image " + imageName);
            task.getDestFile().set(targetCheckpointDockerFile);
            task.getBaseImage().set(configuration.getBaseImage());
            task.setupDockerfileInstructions();
        });

        TaskProvider<DockerBuildImage> dockerBuildTask = tasks.register(adaptTaskName("checkpointBuildImage", imageName), DockerBuildImage.class, task -> {
            task.dependsOn(buildLayersTask, scriptTask);
            task.setGroup(CRAC_TASK_GROUP);
            task.setDescription("Builds a CRaC checkpoint Docker Image");
            if (f.exists()) {
                task.getDockerFile().set(f);
            } else {
                task.getDockerFile()
                        .convention(dockerFileTask.flatMap(Dockerfile::getDestFile));
            }
            task.getInputDir().set(dockerFileTask.flatMap(Dockerfile::getDestDir));
            task.getImages().set(Collections.singletonList("checkpoint"));
        });

        TaskProvider<DockerRemoveContainer> removeContainer = tasks.register(adaptTaskName("checkpointRemoveContainer", imageName), DockerRemoveContainer.class, task -> {
            task.setGroup(CRAC_TASK_GROUP);
            task.setDescription("Removes the checkpoint:latest CRaC checkpoint Docker Image");
            task.getForce().set(true);
            task.getContainerId().set(CRAC_CHECKPOINT);
        });

        TaskProvider<DockerCreateContainer> checkpointContainer = tasks.register(adaptTaskName("checkpointCreateContainer", imageName), DockerCreateContainer.class, task -> {
            task.dependsOn(dockerBuildTask);
            task.setGroup(CRAC_TASK_GROUP);
            task.setDescription("Runs the checkpoint:latest CRaC checkpoint Docker Image");
            task.targetImageId("checkpoint:latest");
            task.getContainerName().set(CRAC_CHECKPOINT);
            task.getHostConfig().getPrivileged().set(true);
            String local = project.getLayout().getBuildDirectory().dir(BUILD_DOCKER_DIRECTORY + imageName + "/cr").map(d -> d.getAsFile().getAbsolutePath()).get();
            task.getHostConfig().getBinds().put(local, "/home/app/cr");
        });
        TaskProvider<DockerStartContainer> start = tasks.register(adaptTaskName("checkpointDockerRun", imageName), DockerStartContainer.class, task -> {
            task.dependsOn(checkpointContainer);
            task.setGroup(CRAC_TASK_GROUP);
            task.setDescription("Runs the checkpoint:latest CRaC checkpoint Docker Image");
            task.getOutputs().dir(project.getLayout().getBuildDirectory().dir(BUILD_DOCKER_DIRECTORY + imageName + "/checkpoint"));
            task.targetContainerId(checkpointContainer.flatMap(DockerCreateContainer::getContainerId));
        });
        TaskProvider<DockerLogsContainer> await = tasks.register("checkpointAwaitSuccess", DockerLogsContainer.class, task -> {
            TeeStringWriter stringWriter = new TeeStringWriter(project.getLogger());
            task.dependsOn(start);
            task.finalizedBy(removeContainer);
            task.getFollow().set(true);
            task.getTailAll().set(true);
            task.getContainerId().set(start.flatMap(DockerExistingContainer::getContainerId));
            task.setSink(stringWriter);
            task.doLast(t -> {
                if (!stringWriter.toString().contains("Snapshotting complete")) {
                    throw new GradleException("Checkpoint container failed");
                }
            });
        });
        start.configure(t -> t.finalizedBy(await));
        return new CheckpointTasksOfNote(f.exists() ? null : dockerFileTask, start);
    }

    private static class CheckpointTasksOfNote {

        private final TaskProvider<CRaCCheckpointDockerfile> checkpointDockerBuild;
        private final TaskProvider<DockerStartContainer> start;

        private CheckpointTasksOfNote(
                @Nullable TaskProvider<CRaCCheckpointDockerfile> checkpointDockerBuild,
                TaskProvider<DockerStartContainer> start
        ) {
            this.checkpointDockerBuild = checkpointDockerBuild;
            this.start = start;
        }

        Optional<TaskProvider<CRaCCheckpointDockerfile>> getCheckpointDockerBuild() {
            return Optional.ofNullable(checkpointDockerBuild);
        }
    }

    private Optional<TaskProvider<CRaCFinalDockerfile>> configureFinalDockerBuild(Project project,
                                                                                  TaskContainer tasks,
                                                                                  TaskProvider<CheckpointScriptTask> scriptTask,
                                                                                  TaskProvider<BuildLayersTask> buildLayersTask,
                                                                                  TaskProvider<DockerStartContainer> start,
                                                                                  CRaCConfiguration configuration,
                                                                                  String imageName) {
        File f = project.file(adaptTaskName("Dockerfile", imageName));
        String dockerFileTaskName = adaptTaskName("dockerfileCrac", imageName);
        Provider<RegularFile> targetCheckpointDockerFile = project.getLayout().getBuildDirectory().file(BUILD_DOCKER_DIRECTORY + imageName + "/Dockerfile");
        TaskProvider<CRaCFinalDockerfile> dockerFileTask = tasks.register(dockerFileTaskName, CRaCFinalDockerfile.class, task -> {
            task.setGroup(CRAC_TASK_GROUP);
            task.setDescription("Builds a Docker File for CRaC checkpointed image " + imageName);
            task.getDestFile().set(targetCheckpointDockerFile);
            task.getBaseImage().set(configuration.getBaseImage());
            task.setupDockerfileInstructions();
        });
        TaskProvider<DockerBuildImage> dockerBuildTask = tasks.register(adaptTaskName("dockerBuildCrac", imageName), DockerBuildImage.class, task -> {
            task.dependsOn(buildLayersTask, scriptTask);
            task.getInputs().dir(start.map(t -> t.getOutputs().getFiles().getSingleFile()));
            task.setGroup(CRAC_TASK_GROUP);
            task.setDescription("Builds a CRaC checkpoint Docker Image (image " + imageName + ")");
            if (f.exists()) {
                task.getDockerFile().set(f);
            } else {
                task.getDockerFile()
                        .convention(dockerFileTask.flatMap(Dockerfile::getDestFile));
            }
            task.getImages().set(Collections.singletonList(project.getName()));
            task.getInputDir().set(dockerFileTask.flatMap(Dockerfile::getDestDir));
        });

        tasks.register(adaptTaskName("dockerPushCrac", imageName), DockerPushImage.class, task -> {
            task.dependsOn(dockerBuildTask);
            task.setGroup("upload");
            task.setDescription("Pushes the " + imageName + " Docker Image");
            task.getImages().set(dockerBuildTask.flatMap(DockerBuildImage::getImages));
        });
        if (!f.exists()) {
            return Optional.of(dockerFileTask);
        }
        return Optional.empty();
    }

}