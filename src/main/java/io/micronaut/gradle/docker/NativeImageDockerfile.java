package io.micronaut.gradle.docker;

import com.bmuschko.gradle.docker.tasks.image.Dockerfile;
import io.micronaut.gradle.graalvm.NativeImageTask;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.StopActionException;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.jvm.Jvm;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Specialization of {@link Dockerfile} for building native images.
 *
 * @author gkrocher
 * @since 1.0.0
 */
public class NativeImageDockerfile extends Dockerfile implements DockerBuildOptions {

    private static final String NATIVE_IMAGE_EXEC_TO_REPLACE = "NATIVE_IMAGE_EXEC";
    private static final String ARGS_TO_REPLACE = "__ARGS__";

    @Input
    private final Property<String> jdkVersion;
    @Input
    private final Property<String> graalVersion;
    @Input
    private final Property<String> graalImage;
    @Input
    @Nullable
    private final Property<String> baseImage;
    @Input
    private final ListProperty<String> args;
    @Input
    private final ListProperty<Integer> exposedPorts;
    @Input
    private final Property<Boolean> requireGraalSdk;
    @Input
    private final Property<DockerBuildStrategy> buildStrategy;
    @Input
    private final Property<String> defaultCommand;


    public NativeImageDockerfile() {
        Project project = getProject();
        setGroup(BasePlugin.BUILD_GROUP);
        setDescription("Builds a Docker File for Native Image");
        getDestFile().set(project.getLayout().getBuildDirectory().file("docker/DockerfileNative"));
        ObjectFactory objects = project.getObjects();
        this.buildStrategy = objects.property(DockerBuildStrategy.class)
                                        .convention(DockerBuildStrategy.DEFAULT);
        this.jdkVersion = objects.property(String.class);
        this.requireGraalSdk = objects.property(Boolean.class).convention(true);
        JavaVersion javaVersion = Jvm.current().getJavaVersion();
        if (javaVersion.isJava11Compatible()) {
            jdkVersion.convention("java11");
        } else {
            jdkVersion.convention("java8");
        }
        this.graalVersion = objects.property(String.class)
                               .convention("21.0.0");
        this.graalImage = objects.property(String.class)
                               .convention(graalVersion.map(version -> "ghcr.io/graalvm/graalvm-ce:" + jdkVersion.get() + '-' + version ));
        this.baseImage = objects.property(String.class)
                                    .convention("null");
        this.args = objects.listProperty(String.class);
        this.exposedPorts = objects.listProperty(Integer.class);
        this.defaultCommand = objects.property(String.class).convention("none");
        doLast(task -> {
            java.io.File f = getDestFile().get().getAsFile();
            System.out.println("Dockerfile written to: " + f.getAbsolutePath());
        });
    }

    /**
     *
     * @return Whether a Graal SDK is required (defaults to 'true').
     */
    public Property<Boolean> getRequireGraalSdk() {
        return requireGraalSdk;
    }

    /**
     * @return The JDK version to use with native image.
     */
    public Property<String> getJdkVersion() {
        return jdkVersion;
    }

    /**
     * @return The build startegy
     */
    public Property<DockerBuildStrategy> getBuildStrategy() {
        return buildStrategy;
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
     * @return The Graal version to use.
     */
    public Property<String> getGraalVersion() {
        return graalVersion;
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
    public Property<String> getDefaultCommand() {
        return this.defaultCommand;
    }

    @Override
    public ListProperty<Integer> getExposedPorts() {
        return this.exposedPorts;
    }

    /**
     * This sets up the commands as a template in the {@link Dockerfile} task
     */
    public void setupDockerfileInstructions() {
        DockerBuildStrategy buildStrategy = this.buildStrategy.getOrElse(DockerBuildStrategy.DEFAULT);
        if (buildStrategy == DockerBuildStrategy.LAMBDA) {
            from(new From("amazonlinux:latest").withStage("graalvm"));
            environmentVariable("LANG", "en_US.UTF-8");
            runCommand("yum install -y gcc gcc-c++ libc6-dev  zlib1g-dev curl bash zlib zlib-devel zip tar gzip");
            String jdkVersion = this.jdkVersion.get();
            String graalVersion = this.graalVersion.get();
            String fileName = "graalvm-ce-" + jdkVersion + "-linux-amd64-" + graalVersion + ".tar.gz";
            runCommand("curl -4 -L https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-" + graalVersion + "/" + fileName + " -o /tmp/" + fileName);
            runCommand("tar -zxf /tmp/" + fileName + " -C /tmp && mv /tmp/graalvm-ce-" + jdkVersion + "-" + graalVersion + " /usr/lib/graalvm");
            runCommand("rm -rf /tmp/*");
            runCommand("/usr/lib/graalvm/bin/gu install native-image");
            defaultCommand("/usr/lib/graalvm/bin/native-image");
            environmentVariable("PATH", "/usr/lib/graalvm/bin:${PATH}");
            from(new From("graalvm").withStage("builder"));
        } else {
            from(new From(graalImage.get()).withStage("graalvm"));
            runCommand("gu install native-image");
        }
        MicronautDockerfile.setupResources(this);
        String baseImage = this.baseImage.get();
        // why I have to do this horrible hack on the Gradle gods know
        if ("null".equalsIgnoreCase(baseImage)) {
            baseImage = null;
        }
        runCommand(NATIVE_IMAGE_EXEC_TO_REPLACE);
        switch (buildStrategy) {
            case ORACLE_FUNCTION:
                if (baseImage == null) {
                    baseImage = "oraclelinux:7-slim";
                }
                from(new From("fnproject/fn-java-fdk:" + getProjectFnVersion()).withStage("fnfdk"));
                from(baseImage);
                workingDir("/function");
                runCommand("groupadd -g 1000 fn && useradd --uid 1000 -g fn fn");
                copyFile(new CopyFile("/home/app/application", "/function/func").withStage("graalvm"));
                copyFile(new CopyFile("/function/runtime/lib/*", ".").withStage("fnfdk"));
                entryPoint("./func", ARGS_TO_REPLACE);
                String cmd = this.defaultCommand.get();
                if ("none".equals(cmd)) {
                    super.defaultCommand("io.micronaut.oraclecloud.function.http.HttpFunction::handleRequest");
                } else {
                    super.defaultCommand(cmd);
                }
            break;
            case LAMBDA:
                if (baseImage == null) {
                    baseImage = "amazonlinux:latest";
                }
                from(baseImage);
                workingDir("/function");
                runCommand("yum install -y zip");
                copyFile(new CopyFile("/home/app/application", "/function/func").withStage("builder"));
                String funcCmd = String.join(" ", "./func", ARGS_TO_REPLACE, "-Djava.library.path=$(pwd)");
                runCommand("echo \"#!/bin/sh\" >> bootstrap && echo \"set -euo pipefail\" >> bootstrap && echo \"" + funcCmd + "\" >> bootstrap");
                runCommand("chmod 777 bootstrap");
                runCommand("chmod 777 func");
                runCommand("zip -j function.zip bootstrap func");
                entryPoint("/function/func");
            break;
            default:
                if (baseImage == null) {
                    baseImage = "frolvlad/alpine-glibc:alpine-3.12";
                }
                from(baseImage);
                if (baseImage.contains("alpine-glibc")) {
                    runCommand("apk update && apk add libstdc++");
                }
                exposePort(this.exposedPorts);
                copyFile(new CopyFile("/home/app/application", "/app/application").withStage("graalvm"));
                entryPoint("/app/application", ARGS_TO_REPLACE);
            break;
        }
    }

    /**
     * This is executed post project evaluation
     */
    void setupNativeImageTaskPostEvaluate() {
        JavaApplication javaApplication = getProject().getExtensions().getByType(JavaApplication.class);
        DockerBuildStrategy buildStrategy = this.buildStrategy.getOrElse(DockerBuildStrategy.DEFAULT);
        Task nit = getProject().getTasks().findByName("nativeImage");
        NativeImageTask nativeImageTask;
        if (nit instanceof NativeImageTask) {
            nativeImageTask = (NativeImageTask) nit;
        } else {
            throw new StopActionException("No native image task present! Must be used in conjunction with a NativeImageTask.");
        }

        // clear out classpath
        nativeImageTask.setClasspath(getProject().files());
        // use hard coded image name
        nativeImageTask.setImageName("application");
        if (buildStrategy == DockerBuildStrategy.ORACLE_FUNCTION) {
            javaApplication.getMainClass().set("com.fnproject.fn.runtime.EntryPoint");
            nativeImageTask.setMain("com.fnproject.fn.runtime.EntryPoint");
            nativeImageTask.args("--report-unsupported-elements-at-runtime");
        } else if (buildStrategy == DockerBuildStrategy.LAMBDA) {
            if (!javaApplication.getMainClass().isPresent()) {
                javaApplication.getMainClass().set("io.micronaut.function.aws.runtime.MicronautLambdaRuntime");
            }
            if (!nativeImageTask.getMain().isPresent()) {
                nativeImageTask.setMain("io.micronaut.function.aws.runtime.MicronautLambdaRuntime");
            }
        } else if (!nativeImageTask.getMain().isPresent()) {
            nativeImageTask.setMain(javaApplication.getMainClass().get());
        }
        nativeImageTask.configure();

        List<String> commandLine = nativeImageTask.getCommandLine();
        commandLine.add("-cp");
        commandLine.add("/home/app/libs/*.jar:/home/app/resources:/home/app/application.jar");
        // add --static if image is scratch
        if (baseImage != null && baseImage.isPresent() && baseImage.get().equalsIgnoreCase("scratch") &&
                !commandLine.contains("--static")) {
            commandLine.add("--static");
        }
        String nativeImageCommand = String.join(" ", commandLine);
        List<Instruction> instructions = new ArrayList<>(getInstructions().get());
        getInstructions().set(instructions.stream().map(i -> {
            if (i instanceof RunCommandInstruction && i.getText().contains(NATIVE_IMAGE_EXEC_TO_REPLACE)) {
                return new RunCommandInstruction(nativeImageCommand);
            }
            else if (i instanceof EntryPointInstruction && i.getText().contains(ARGS_TO_REPLACE)) {
                return new EntryPointInstruction(i.getText()
                        .replace(i.getKeyword(), "")
                        .replace(ARGS_TO_REPLACE, String.join(" ", args.get())));
            }
            else if (i instanceof RunCommandInstruction && i.getText().contains(ARGS_TO_REPLACE)) {
                return new RunCommandInstruction(i.getText()
                        .replace(i.getKeyword(), "")
                        .replace(ARGS_TO_REPLACE, String.join(" ", args.get())));
            }
            return i;
        }).collect(Collectors.toList()));
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

    private String getProjectFnVersion() {
        JavaVersion javaVersion = Jvm.current().getJavaVersion();
        if (javaVersion != null && javaVersion.isJava11Compatible()) {
            return "jre11-latest";
        }
        return "latest";
    }
}
