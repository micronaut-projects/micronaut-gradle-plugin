package io.micronaut.gradle.crac;

import com.bmuschko.gradle.docker.tasks.image.Dockerfile;
import io.micronaut.gradle.docker.DockerBuildStrategy;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@CacheableTask
public abstract class CRaCCheckpointDockerfile extends Dockerfile {
    public static final String DEFAULT_WORKING_DIR = "/home/app";

    @Input
    public abstract Property<String> getBaseImage();

    @Input
    @Optional
    public abstract Property<String> getPlatform();

    @Input
    public abstract ListProperty<String> getArgs();

    @Input
    public abstract Property<DockerBuildStrategy> getBuildStrategy();

    @Input
    public abstract Property<String> getTargetWorkingDirectory();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    @Optional
    public abstract RegularFileProperty getCustomCheckpointDockerfile();

    @SuppressWarnings("java:S5993") // Gradle API
    public CRaCCheckpointDockerfile() {
        setGroup(BasePlugin.BUILD_GROUP);
        setDescription("Builds the CRaC checkpoint Docker File for a Micronaut application");
        this.getBuildStrategy().convention(DockerBuildStrategy.DEFAULT);
        this.getBaseImage().convention("none");
        this.getTargetWorkingDirectory().convention(DEFAULT_WORKING_DIR);
    }

    @TaskAction
    @Override
    @SuppressWarnings("java:S106") // System.out
    public void create() throws IOException {
        if (getCustomCheckpointDockerfile().isPresent()) {
            try {
                Path source = getCustomCheckpointDockerfile().get().getAsFile().toPath();
                Files.copy(
                        source,
                        getDestFile().get().getAsFile().toPath()
                );
                getProject().getLogger().lifecycle("Checkpoint Dockerfile copied from {} to {}", source, getDestFile().get().getAsFile().getAbsolutePath());
                return;
            } catch (IOException e) {
                throw new GradleException("Error copying custom checkpoint Dockerfile", e);
            }
        }
        super.create();
        getProject().getLogger().lifecycle("Checkpoint Dockerfile written to: {}", getDestFile().get().getAsFile().getAbsolutePath());
    }

    private void setupInstructions(List<Instruction> additionalInstructions) {
        DockerBuildStrategy strategy = this.getBuildStrategy().getOrElse(DockerBuildStrategy.DEFAULT);
        String from = getBaseImage().getOrNull();
        if ("none".equalsIgnoreCase(from)) {
            from = null;
        }
        switch (strategy) {
            case ORACLE_FUNCTION:
                throw new GradleException("Oracle Functions are not supported for the CRaC plugin");
            case LAMBDA:
                throw new GradleException("Lambda Functions are not supported for the CRaC plugin");
            default:
                from(getPlatform().map(p -> "--platform=" + p + " ").getOrElse("") + from);
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

    static void setupResources(Dockerfile task) {
        String workDir = DEFAULT_WORKING_DIR;
        task.workingDir(workDir);
        task.instruction("# Add required libraries");
        task.runCommand("apt-get update && apt-get install -y \\\n" +
                "        curl \\\n" +
                "        jq \\\n" +
                "        libnl-3-200 \\\n" +
                "    && rm -rf /var/lib/apt/lists/*");
        task.instruction("# Install latest CRaC OpenJDK");
        task.runCommand("release=\"$(curl -sL https://api.github.com/repos/CRaC/openjdk-builds/releases/latest)\" \\\n" +
                "    && asset=\"$(echo $release | sed -e 's/\\r//g' | sed -e 's/\\x09//g' | tr '\\n' ' ' | jq '.assets[] | select(.name | test(\"openjdk-[0-9]+-crac\\\\+[0-9]+_linux-x64\\\\.tar\\\\.gz\"))')\" \\\n" +
                "    && id=\"$(echo $asset | jq .id)\" \\\n" +
                "    && name=\"$(echo $asset | jq -r .name)\" \\\n" +
                "    && curl -LJOH 'Accept: application/octet-stream' \"https://api.github.com/repos/CRaC/openjdk-builds/releases/assets/$id\" >&2 \\\n" +
                "    && tar xzf \"$name\" \\\n" +
                "    && mv ${name%%.tar.gz} /azul-crac-jdk \\\n" +
                "    && rm \"$name\"");
        task.instruction("# Copy layers");
        task.copyFile("layers/libs", workDir + "/libs");
        task.copyFile("layers/classes", workDir + "/classes");
        task.copyFile("layers/resources", workDir + "/resources");
        task.copyFile("layers/application.jar", workDir + "/application.jar");
        task.instruction("# Add build scripts");
        task.copyFile("scripts/checkpoint.sh", workDir + "/checkpoint.sh");
        task.copyFile("scripts/warmup.sh", workDir + "/warmup.sh");
    }
}
