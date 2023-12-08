package io.micronaut.gradle.crac;

import com.bmuschko.gradle.docker.tasks.image.Dockerfile;
import io.micronaut.gradle.docker.DockerBuildStrategy;
import io.micronaut.gradle.docker.DockerfileEditor;
import io.micronaut.gradle.docker.MicronautDockerfile;
import io.micronaut.gradle.docker.model.Layer;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
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
import org.gradle.jvm.toolchain.JavaLanguageVersion;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static io.micronaut.gradle.crac.MicronautCRaCPlugin.ARM_ARCH;
import static io.micronaut.gradle.crac.MicronautCRaCPlugin.X86_64_ARCH;

@CacheableTask
public abstract class CRaCCheckpointDockerfile extends Dockerfile {

    public static final String DEFAULT_WORKING_DIR = "/home/app";

    @Input
    public abstract Property<String> getBaseImage();

    @Input
    @Optional
    @SuppressWarnings("java:S6355") // We need Java 8... Java 8 doesn't have forRemoval and since
    @Deprecated
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

    @Input
    public abstract Property<String> getArch();

    @Input
    public abstract Property<JavaLanguageVersion> getJavaVersion();

    /**
     * The layers to copy to the image.
     * @return the layers
     */
    @Input
    public abstract ListProperty<Layer> getLayers();

    /**
     * If true, the COPY command will use --link option when copying files from the build context.
     * Defaults to true.
     * @return The use copy link property
     */
    @Input
    @Optional
    public abstract Property<Boolean> getUseCopyLink();

    @Inject
    protected abstract ObjectFactory getObjects();

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
        applyStandardTransforms();
        getProject().getLogger().lifecycle("Checkpoint Dockerfile written to: {}", getDestFile().get().getAsFile().getAbsolutePath());
    }


    protected void applyStandardTransforms() {
        if (Boolean.TRUE.equals(getUseCopyLink().getOrElse(true))) {
            DockerfileEditor.apply(getObjects(), this, List.of(
                editor -> editor.replaceRegex("COPY (?!--link)(.*)", "COPY --link $1")
            ));
        }
    }

    @SuppressWarnings("java:S5738") // Using deprecated method still, until it's removal in 4.0.0
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

    static void setupResources(CRaCCheckpointDockerfile task) {
        String workDir = DEFAULT_WORKING_DIR;
        task.workingDir(workDir);
        task.instruction("# Add required libraries");
        task.runCommand("apt-get update && apt-get install -y \\\n" +
                "        curl \\\n" +
                "        jq \\\n" +
                "        libnl-3-200 \\\n" +
                "    && rm -rf /var/lib/apt/lists/*");
        task.instruction("# Install latest CRaC OpenJDK");

        // Limit the architecture, Azul doesn't support x86_64 https://api.azul.com/metadata/v1/docs/swagger
        String arch = task.getArch().map(a -> ARM_ARCH.equals(a) ? ARM_ARCH : X86_64_ARCH).get();

        String javaVersion = task.getJavaVersion().map(JavaLanguageVersion::toString).get();

        String errorMessage = "No CRaC OpenJDK found for Java version " + javaVersion + " and architecture " + arch;

        String url = "https://api.azul.com/metadata/v1/zulu/packages/?java_version=" + javaVersion + "&arch=" + arch + "&crac_supported=true&java_package_type=jdk&latest=true&release_status=ga&certifications=tck&page=1&page_size=100";
        task.runCommand("release_id=$(curl -s \"" + url + "\" -H \"accept: application/json\" | jq -r '.[0] | .package_uuid') \\\n" +
                "    && if [ \"$release_id\" = \"null\" ]; then \\\n" +
                "           echo \"" + errorMessage + "\"; \\\n" +
                "           exit 1; \\\n" +
                "       fi \\\n" +
                "    && details=$(curl -s \"https://api.azul.com/metadata/v1/zulu/packages/$release_id\" -H \"accept: application/json\") \\\n" +
                "    && name=$(echo \"$details\" | jq -r '.name') \\\n" +
                "    && url=$(echo \"$details\" | jq -r '.download_url') \\\n" +
                "    && hash=$(echo \"$details\" | jq -r '.sha256_hash') \\\n" +
                "    && echo \"Downloading $name from $url\" \\\n" +
                "    && curl -LJOH 'Accept: application/octet-stream' \"$url\" >&2 \\\n" +
                "    && file_sha=$(sha256sum -b \"$name\" | cut -d' ' -f 1) \\\n" +
                "    && if [ \"$file_sha\" != \"$hash\" ]; then \\\n" +
                "           echo \"SHA256 hash mismatch: $file_sha != $hash\"; \\\n" +
                "           exit 1; \\\n" +
                "       fi \\\n" +
                "    && echo \"SHA256 hash matches: $file_sha == $hash\" \\\n" +
                "    && tar xzf \"$name\" \\\n" +
                "    && mv ${name%%.tar.gz} /azul-crac-jdk \\\n" +
                "    && rm \"$name\"");

        task.instruction("# Copy layers");
        MicronautDockerfile.setupResources(task, task.getLayers().get(), workDir);

        task.instruction("# Add build scripts");
        task.copyFile("scripts/checkpoint.sh", workDir + "/checkpoint.sh");
        task.copyFile("scripts/warmup.sh", workDir + "/warmup.sh");
    }
}
