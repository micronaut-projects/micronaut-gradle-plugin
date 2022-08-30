package io.micronaut.gradle.crac;

import com.bmuschko.gradle.docker.tasks.image.Dockerfile;
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
import java.util.List;

public class CRaCCheckpointDockerfile extends Dockerfile {
    public static final String DEFAULT_WORKING_DIR = "/home/app";

    @Input
    private final Property<String> baseImage;
    @Input
    private final ListProperty<String> args;
    @Input
    private final Property<DockerBuildStrategy> buildStrategy;

    @Input
    private final Property<String> targetWorkingDirectory;

    public CRaCCheckpointDockerfile() {
        Project project = getProject();
        setGroup(BasePlugin.BUILD_GROUP);
        setDescription("Builds the CRaC checkpoint Docker File for a Micronaut application");
        ObjectFactory objects = project.getObjects();
        this.buildStrategy = objects.property(DockerBuildStrategy.class)
                                    .convention(DockerBuildStrategy.DEFAULT);
        this.baseImage = objects.property(String.class).convention("none");
        this.args = objects.listProperty(String.class);
        this.targetWorkingDirectory = objects.property(String.class).convention(DEFAULT_WORKING_DIR);
    }

    public Property<String> getTargetWorkingDirectory() {
        return targetWorkingDirectory;
    }

    @TaskAction
    @Override
    @SuppressWarnings("java:S106") // System.out
    public void create() {
        super.create();
        System.out.println("Dockerfile written to: " + getDestFile().get().getAsFile().getAbsolutePath());
    }

    private void setupInstructions(List<Instruction> additionalInstructions) {
        DockerBuildStrategy strategy = this.buildStrategy.getOrElse(DockerBuildStrategy.DEFAULT);
        String from = baseImage.getOrNull();
        if ("none".equalsIgnoreCase(from)) {
            from = null;
        }
        switch (strategy) {
            case ORACLE_FUNCTION:
                throw new GradleException("Oracle Functions are not supported for the CRaC plugin");
            case LAMBDA:
                throw new GradleException("Lambda Functions are not supported for the CRaC plugin");
            default:
                from("--platform=linux/amd64 " + from);
                setupResources(this);
                getInstructions().addAll(additionalInstructions);
                if (getInstructions().get().stream().noneMatch(instruction -> instruction.getKeyword().equals(EntryPointInstruction.KEYWORD))) {
                    entryPoint(getArgs().map(strings -> {
                        List<String> newList = new ArrayList<>(strings.size() + 3);
                        newList.add("/home/app/checkpoint.sh");
                        newList.addAll(strings);
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

    public Property<String> getBaseImage() {
        return baseImage;
    }

    /**
     * @return The build strategy.
     */
    public Property<DockerBuildStrategy> getBuildStrategy() {
        return buildStrategy;
    }

    public ListProperty<String> getArgs() {
        return args;
    }

    static void setupResources(Dockerfile task) {
        String workDir = DEFAULT_WORKING_DIR;
        task.workingDir(workDir);
        task.instruction("# Add required libraries");
        task.runCommand("apt-get update \\\n" +
                "    && apt-get install -y curl jq libnl-3-200");
        task.instruction("# Install latest CRaC OpenJDK");
        task.runCommand("release=\"$(curl -sL https://api.github.com/repos/CRaC/openjdk-builds/releases/latest)\" \\\n" +
                "    && asset=\"$(echo $release | jq '.assets[] | select(.name | test(\"jdk[0-9]+-crac\\\\+[0-9]+\\\\.tar\\\\.gz\"))')\" \\\n" +
                "    && id=\"$(echo $asset | jq .id)\" \\\n" +
                "    && name=\"$(echo $asset | jq -r .name)\" \\\n" +
                "    && curl -LJOH 'Accept: application/octet-stream' \"https://api.github.com/repos/CRaC/openjdk-builds/releases/assets/$id\" >&2 \\\n" +
                "    && tar xzf \"$name\" \\\n" +
                "    && mv ${name%%.tar.gz} /azul-crac-jdk \\\n" +
                "    && rm \"$name\"");
        task.copyFile("layers/libs", workDir + "/libs");
        task.copyFile("layers/classes", workDir + "/classes");
        task.copyFile("layers/resources", workDir + "/resources");
        task.copyFile("layers/application.jar", workDir + "/application.jar");
        task.copyFile("checkpoint/checkpoint.sh", workDir + "/checkpoint.sh");
        task.copyFile("checkpoint/warmup.sh", workDir + "/warmup.sh");
    }
}
