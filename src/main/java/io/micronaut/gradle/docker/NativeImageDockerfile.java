package io.micronaut.gradle.docker;

import com.bmuschko.gradle.docker.tasks.image.Dockerfile;
import io.micronaut.gradle.MicronautApplicationPlugin;
import io.micronaut.gradle.MicronautExtension;
import io.micronaut.gradle.MicronautRuntime;
import io.micronaut.gradle.graalvm.NativeImageTask;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

import javax.annotation.Nullable;
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
    @Nullable
    private final Property<String> baseImage;
    @Input
    private final ListProperty<String> args;
    @Input
    private final ListProperty<Integer> exposedPorts;
    private final Property<MicronautRuntime> micronautRuntime;


    public NativeImageDockerfile() {
        Project project = getProject();
        setGroup(BasePlugin.BUILD_GROUP);
        setDescription("Builds a Docker File for Native Image");
        getDestFile().set(project.getLayout().getBuildDirectory().file("docker/DockerfileNative"));
        ObjectFactory objects = project.getObjects();
        this.micronautRuntime = objects.property(MicronautRuntime.class);
        this.graalImage = objects.property(String.class)
                            .convention("oracle/graalvm-ce:20.2.0-java11");
        this.baseImage = objects.property(String.class)
                                    .convention("null");
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
     * @return The micronaut runtime
     */
    @Input
    public Property<MicronautRuntime> getMicronautRuntime() {
        return micronautRuntime;
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
    @Nullable
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
        MicronautRuntime micronautRuntime = this.micronautRuntime.getOrElse(MicronautRuntime.NETTY);

        from(new From(graalImage.get()).withStage("graalvm"));
        runCommand("gu install native-image");
        MicronautDockerfile.setupResources(this);
        // clear out classpath
        this.nativeImageTask.setClasspath(getProject().files());
        // use hard coded image name
        this.nativeImageTask.setImageName("application");
        if (micronautRuntime == MicronautRuntime.ORACLE_FUNCTION) {
            this.nativeImageTask.setMain("com.fnproject.fn.runtime.EntryPoint");
            this.nativeImageTask.args("--report-unsupported-elements-at-runtime");
        } else if (micronautRuntime == MicronautRuntime.LAMBDA) {
            this.nativeImageTask.setMain("io.micronaut.function.aws.runtime.MicronautLambdaRuntime");
        }
        this.nativeImageTask.configure();
        List<String> commandLine = this.nativeImageTask.getCommandLine();
        commandLine.add("-cp");
        commandLine.add("/home/app/libs/*.jar:/home/app/resources:/home/app/application.jar");
        String baseImage = this.baseImage.get();
        // why I have to do this horrible hack on the Gradle gods know
        if ("null".equalsIgnoreCase(baseImage)) {
            baseImage = null;
        }
        // add --static if image is scratch
        if (baseImage != null && baseImage.equalsIgnoreCase("scratch") && !commandLine.contains("--static")) {
            commandLine.add("--static");
        }
        runCommand(String.join(" ", commandLine));
        switch (micronautRuntime) {
            case ORACLE_FUNCTION:
                if (baseImage == null) {
                    baseImage = "oraclelinux:7-slim";
                }
                from(new From("fnproject/fn-java-fdk:1.0.105").withStage("fnfdk"));
                from(baseImage);
                workingDir("/function");
                runCommand("groupadd -g 1000 fn && useradd --uid 1000 -g fn fn");
                copyFile(new CopyFile("/home/app/application", "/function/func").withStage("graalvm"));
                copyFile(new CopyFile("/function/runtime/lib/*", ".").withStage("fnfdk"));
                entryPoint(args.map(strings -> {
                    List<String> newList = new ArrayList<>(strings.size() + 1);
                    newList.add("./func");
                    newList.addAll(strings);
                    return newList;
                }));
                defaultCommand("io.micronaut.oci.function.http.HttpFunction::handleRequest");
            break;
            case LAMBDA:
                // TODO: handle lambda
//                if (baseImage == null) {
//                    baseImage = "amazonlinux:latest";
//                }
            default:
                if (baseImage == null) {
                    baseImage = "frolvlad/alpine-glibc";
                }
                from(baseImage);
                if (baseImage.contains("alpine-glibc")) {
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
            break;
        }
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
