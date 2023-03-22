package io.micronaut.gradle.crac;

import io.micronaut.gradle.docker.DockerBuildStrategy;
import io.micronaut.gradle.docker.MicronautDockerfile;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static io.micronaut.gradle.crac.MicronautCRaCPlugin.createCheckpointImageName;

@CacheableTask
public abstract class CRaCFinalDockerfile extends MicronautDockerfile {

    @Input
    @Optional
    public abstract Property<String> getPlatform();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    @Optional
    public abstract RegularFileProperty getCustomFinalDockerfile();

    public static final String DEFAULT_WORKING_DIR = "/home/app";

    @SuppressWarnings("java:S5993") // Gradle API
    public CRaCFinalDockerfile() {
        setDescription("Builds a Docker File for a CRaC checkpointed Micronaut application");
    }

    @TaskAction
    @Override
    public void create() throws IOException {
        if (getCustomFinalDockerfile().isPresent()) {
            Path source = getCustomFinalDockerfile().get().getAsFile().toPath();
            try {
                Files.copy(
                        source,
                        getDestFile().get().getAsFile().toPath()
                );
                getProject().getLogger().lifecycle("Dockerfile copied from {} to {}", source, getDestFile().get().getAsFile().getAbsolutePath());
                return;
            } catch (IOException e) {
                throw new GradleException("Error copying custom final Dockerfile", e);
            }
        }
        super.create();
        getProject().getLogger().lifecycle("Dockerfile written to: {}", getDestFile().get().getAsFile().getAbsolutePath());
    }

    @Override
    protected void setupInstructions(List<Instruction> additionalInstructions) {
        DockerBuildStrategy effectiveBuildStrategy = this.buildStrategy.getOrElse(DockerBuildStrategy.DEFAULT);
        String from = getBaseImage().getOrNull();
        if ("none".equalsIgnoreCase(from)) {
            from = null;
        }
        switch (effectiveBuildStrategy) {
            case ORACLE_FUNCTION:
                throw new GradleException("Oracle Functions are not supported for the CRaC plugin");
            case LAMBDA:
                throw new GradleException("Lambda Functions are not supported for the CRaC plugin");
            default:
                from(getPlatform().map(p -> "--platform=" + p + " ").getOrElse("") + from);
                setupResources();
                exposePort(exposedPorts);
                getInstructions().addAll(additionalInstructions);
                if (getInstructions().get().stream().noneMatch(instruction -> instruction.getKeyword().equals(EntryPointInstruction.KEYWORD))) {
                    entryPoint(getArgs().map(strings -> {
                        List<String> newList = new ArrayList<>(strings.size() + 3);
                        newList.add("/home/app/run.sh");
                        newList.addAll(strings);
                        return newList;
                    }));
                }
        }
    }

    private void setupResources() {
        String workDir = DEFAULT_WORKING_DIR;
        workingDir(workDir);
        instruction("# Add required libraries");
        runCommand("apt-get update && apt-get install -y \\\n" +
                "        libnl-3-200 \\\n" +
                "    && rm -rf /var/lib/apt/lists/*");
        instruction("# Copy CRaC JDK from the checkpoint image (to save a download)");
        copyFile("--from=" + createCheckpointImageName(getProject()) + " /azul-crac-jdk", "/azul-crac-jdk");
        instruction("# Copy layers");
        copyFile("cr", workDir + "/cr");
        copyFile("layers/libs", workDir + "/libs");
        copyFile("layers/classes", workDir + "/classes");
        copyFile("layers/resources", workDir + "/resources");
        copyFile("layers/application.jar", workDir + "/application.jar");
        copyFile("scripts/run.sh", workDir + "/run.sh");
    }
}
