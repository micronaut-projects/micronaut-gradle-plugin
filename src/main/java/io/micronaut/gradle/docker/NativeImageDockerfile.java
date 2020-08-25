package io.micronaut.gradle.docker;

import com.bmuschko.gradle.docker.tasks.image.Dockerfile;
import io.micronaut.gradle.MicronautExtension;
import io.micronaut.gradle.graalvm.NativeImageTask;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Specialization of {@link Dockerfile} for building native images.
 *
 * @author gkrocher
 * @since 1.0.0
 */
public class NativeImageDockerfile extends Dockerfile implements DockerBuildOptions {
    private final NativeImageTask nativeImageTask;

    @Input
    private final Property<String> graalImage;
    @Input
    private final Property<String> baseImage;
    @Input
    private final ListProperty<String> args;
    @Input
    private ListProperty<Integer> exposedPorts;

    public NativeImageDockerfile() {
        Project project = getProject();
        setGroup(BasePlugin.BUILD_GROUP);
        setDescription("Builds a Docker File for Native Image");
        getDestFile().set(project.getLayout().getBuildDirectory().file("docker/DockerfileNative"));
        ObjectFactory objects = project.getObjects();
        this.graalImage = objects.property(String.class)
                            .convention("oracle/graalvm-ce:20.2.0-java11");
        this.baseImage = objects.property(String.class)
                            .convention("frolvlad/alpine-glibc");
        Task nit = project.getTasks().getByName("nativeImage");
        if (nit instanceof NativeImageTask) {
            this.nativeImageTask = (NativeImageTask) nit;
        } else {
            throw new IllegalStateException("No native image task present! Must be used in conjunction with a NativeImageTask.");
        }
        this.args = objects.listProperty(String.class);
        this.exposedPorts = objects.listProperty(Integer.class);
    }

    /**
     * @return The arguments to pass to the native image executable when starting up in the docker container.
     */
    @Override
    public ListProperty<String> getArgs() {
        return args;
    }

    /**
     * @return the GraalVM docker image to use
     */
    public Property<String> getGraalImage() {
        return graalImage;
    }

    /**
     * @return The base image to use
     */
    @Override
    public Property<String> getBaseImage() {
        return baseImage;
    }

    @Override
    public ListProperty<Integer> getExposedPorts() {
        return this.exposedPorts;
    }

    @Override
    @TaskAction
    public void create() {
        MicronautExtension micronautExtension = getProject().getExtensions().getByType(MicronautExtension.class);
        from(new From(graalImage.get()).withStage("graalvm"));
        runCommand("gu install native-image");
        MicronautDockerfile.setupResources(this);
        // clear out classpath
        this.nativeImageTask.setClasspath(getProject().files());
        // use hard coded image name
        this.nativeImageTask.setImageName("application");
        this.nativeImageTask.configure();
        List<String> commandLine = this.nativeImageTask.getCommandLine();
        commandLine.add("-cp");
        commandLine.add("/home/app/libs/*.jar:/home/app/resources:/home/app/application.jar");
        String bi = baseImage.get();
        // add --static if image is scratch
        if (bi.equalsIgnoreCase("scratch") && !commandLine.contains("--static")) {
            commandLine.add("--static");
        }
        runCommand(String.join(" ", commandLine));
        from(bi);
        if (bi.contains("alpine-glibc")) {
            runCommand("apk update && apk add libstdc++");
        }
        exposePort(this.exposedPorts);
        copyFile(new CopyFile("/home/app/application", "/app/application").withStage("graalvm"));
        entryPoint(args.map(strings -> {
            List<String> newList = new ArrayList<>(strings.size() + 1);
            newList.add("/app/application");
            newList.addAll(strings);
            return newList;
        }));
        super.create();
    }

    /**
     * Adds additional args to pass to the native image executable.
     * @param args The args
     * @return This instance.
     */
    @Override
    public NativeImageDockerfile args(String... args) {
        this.args.addAll(args);
        return this;
    }

    @Override
    public NativeImageDockerfile baseImage(String imageName) {
        if (imageName != null) {
            this.baseImage.set(imageName);
        }
        return this;
    }

    @Override
    public DockerBuildOptions exportPorts(Integer... ports) {
        this.exposedPorts.set(Arrays.asList(ports));
        return this;
    }

    public NativeImageDockerfile graalImage(String imageName) {
        if (imageName != null) {
            this.graalImage.set(imageName);
        }
        return this;
    }
}
