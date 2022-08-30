package io.micronaut.gradle.crac;

import com.bmuschko.gradle.docker.tasks.image.Dockerfile;
import io.micronaut.gradle.docker.DockerBuildOptions;
import io.micronaut.gradle.docker.DockerBuildStrategy;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CRaCFinalDockerfile extends Dockerfile implements DockerBuildOptions {
    public static final String DEFAULT_WORKING_DIR = "/home/app";

    @Input
    private final Property<String> baseImage;
    @Input
    private final ListProperty<String> args;
    @Input
    private final ListProperty<Integer> exposedPorts;
    @Input
    private final Property<DockerBuildStrategy> buildStrategy;
    @Input
    private final Property<String> defaultCommand;

    @Input
    private final Property<String> targetWorkingDirectory;

    public CRaCFinalDockerfile() {
        Project project = getProject();
        setGroup(BasePlugin.BUILD_GROUP);
        setDescription("Builds a Docker File for a Micronaut application");
        ObjectFactory objects = project.getObjects();
        this.buildStrategy = objects.property(DockerBuildStrategy.class)
                                    .convention(DockerBuildStrategy.DEFAULT);
        this.baseImage = objects.property(String.class).convention("none");
        this.defaultCommand = objects.property(String.class).convention("none");
        this.args = objects.listProperty(String.class);
        this.exposedPorts = objects.listProperty(Integer.class)
                    .convention(Collections.singletonList(8080));
        this.targetWorkingDirectory = objects.property(String.class).convention(DEFAULT_WORKING_DIR);
    }

    public Property<String> getTargetWorkingDirectory() {
        return targetWorkingDirectory;
    }

    @Override
    public Property<String> getDefaultCommand() {
        return defaultCommand;
    }

    @TaskAction
    @Override
    public void create() {
        super.create();
        System.out.println("Dockerfile written to: " + getDestFile().get().getAsFile().getAbsolutePath());
    }

    private void setupInstructions(List<Instruction> additionalInstructions) {
        String workDir = getTargetWorkingDirectory().get();
        DockerBuildStrategy buildStrategy = this.buildStrategy.getOrElse(DockerBuildStrategy.DEFAULT);
        String from = getBaseImage().getOrNull();
        if ("none".equalsIgnoreCase(from)) {
            from = null;
        }
        switch (buildStrategy) {
            case ORACLE_FUNCTION:
                throw new GradleException("Oracle Functions are not supported for the CRaC plugin");
            case LAMBDA:
                throw new GradleException("Lambda Functions are not supported for the CRaC plugin");
            default:
                from("--platform=linux/amd64 " + from);
                setupResources(this);
                exposePort(exposedPorts);
                getInstructions().addAll(additionalInstructions);
                if (getInstructions().get().stream().noneMatch(instruction -> instruction.getKeyword().equals(EntryPointInstruction.KEYWORD))) {
                    entryPoint(getArgs().map(strings -> {
                        List<String> newList = new ArrayList<>(strings.size() + 3);
                        newList.add("/azul-crac-jdk/bin/java");
                        newList.addAll(strings);
                        newList.add("-XX:CRaCRestoreFrom=cr");
                        return newList;
                    }));
                }
        }
    }

    /**
     * The Dockerfile task requires a 'from' at least, but this
     * will be replaced in setupTaskPostEvaluate where we also
     * incorporate commands supplied by the build.gradle file (if required)
     */
    void setupDockerfileInstructions() {
        from("placeholder");
    }

    /**
     * This is executed post project evaluation
     */
    void setupTaskPostEvaluate() {
        // Get any custom instructions the user may or may not have entered, but ignoring our 'from' placeholder
        List<Instruction> additionalInstructions = new ArrayList<>(getInstructions().get().subList(1, getInstructions().get().size()));
        // Reset the instructions to empty
        getInstructions().set(new ArrayList<>());
        setupInstructions(additionalInstructions);
    }

    /**
     * @return The build strategy.
     */
    public Property<DockerBuildStrategy> getBuildStrategy() {
        return buildStrategy;
    }

    @Override
    public ListProperty<String> getArgs() {
        return args;
    }

    @Override
    public Property<String> getBaseImage() {
        return baseImage;
    }

    @Override
    public ListProperty<Integer> getExposedPorts() {
        return this.exposedPorts;
    }

    @Override
    public DockerBuildOptions args(String... args) {
        this.args.addAll(args);
        return this;
    }

    @Override
    public DockerBuildOptions baseImage(String imageName) {
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

    static void setupResources(Dockerfile task) {
        String workDir = DEFAULT_WORKING_DIR;
        task.workingDir(workDir);
        task.instruction("# Add required libraries");
        task.runCommand("apt-get update \\\n" +
                "    && apt-get install -y libnl-3-200");
        task.instruction("# Copy CRaC JDK from the checkpoint image (to save a download)");
        task.copyFile("--from=checkpoint /azul-crac-jdk", "/azul-crac-jdk");
        task.instruction("# Copy layers");
        task.copyFile("cr", workDir + "/cr");
        task.copyFile("layers/libs", workDir + "/libs");
        task.copyFile("layers/classes", workDir + "/classes");
        task.copyFile("layers/resources", workDir + "/resources");
        task.copyFile("layers/application.jar", workDir + "/application.jar");
    }
}
