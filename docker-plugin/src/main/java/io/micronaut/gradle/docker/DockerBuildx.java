package io.micronaut.gradle.docker;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

/**
 * Executes {@code docker buildx build} for multi-platform image builds.
 *
 * @since 5.0.0
 */
public abstract class DockerBuildx extends DefaultTask {
    private final ListProperty<String> platforms;
    private final ListProperty<String> images;
    private final Property<String> builder;
    private final RegularFileProperty dockerFile;
    private final DirectoryProperty inputDir;
    private final Property<String> dockerExecutable;

    public DockerBuildx() {
        setGroup(BasePlugin.BUILD_GROUP);
        ObjectFactory objects = getProject().getObjects();
        this.platforms = objects.listProperty(String.class).convention(List.of());
        this.images = objects.listProperty(String.class).convention(List.of());
        this.builder = objects.property(String.class);
        this.dockerFile = objects.fileProperty();
        this.inputDir = objects.directoryProperty();
        this.dockerExecutable = objects.property(String.class).convention("docker");
    }

    @Input
    public ListProperty<String> getPlatforms() {
        return platforms;
    }

    public void setPlatforms(List<String> platforms) {
        getPlatforms().set(requireNoNullValues("platforms", platforms));
    }

    @Input
    public ListProperty<String> getImages() {
        return images;
    }

    public void setImages(List<String> images) {
        getImages().set(requireNoNullValues("images", images));
    }

    @Input
    @Optional
    public Property<String> getBuilder() {
        return builder;
    }

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public RegularFileProperty getDockerFile() {
        return dockerFile;
    }

    public void setDockerFile(RegularFile dockerFile) {
        getDockerFile().set(dockerFile);
    }

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public DirectoryProperty getInputDir() {
        return inputDir;
    }

    @Internal
    public Property<String> getDockerExecutable() {
        return dockerExecutable;
    }

    @Inject
    protected abstract ExecOperations getExecOperations();

    @TaskAction
    void buildImage() {
        validate("platforms", getPlatforms().get());
        validate("images", getImages().get());
        getExecOperations().exec(spec -> {
            spec.executable(getDockerExecutable().get());
            spec.args(buildCommandLine());
        });
    }

    List<String> buildCommandLine() {
        var args = new ArrayList<String>();
        args.add("buildx");
        args.add("build");
        if (getBuilder().isPresent() && !getBuilder().get().trim().isEmpty()) {
            args.add("--builder");
            args.add(getBuilder().get().trim());
        }
        args.add("--platform");
        args.add(String.join(",", trimmedValuesOf(getPlatforms().get())));
        for (String image : trimmedValuesOf(getImages().get())) {
            args.add("--tag");
            args.add(image);
        }
        args.add("--push");
        args.add("--file");
        args.add(getDockerFile().get().getAsFile().getAbsolutePath());
        args.add(getInputDir().get().getAsFile().getAbsolutePath());
        return args;
    }

    private static void validate(String propertyName, List<String> values) {
        if (values.isEmpty()) {
            throw new GradleException("Property '" + propertyName + "' must not be empty.");
        }
        for (String value : values) {
            if (value == null) {
                throw new GradleException("Property '" + propertyName + "' must not contain null values.");
            }
            if (value.trim().isEmpty()) {
                throw new GradleException("Property '" + propertyName + "' must not contain blank values.");
            }
        }
    }

    private static List<String> trimmedValuesOf(List<String> values) {
        return values.stream()
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .toList();
    }

    private static List<String> requireNoNullValues(String propertyName, List<String> values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value == null) {
                throw new GradleException("Property '" + propertyName + "' must not contain null values.");
            }
        }
        return values;
    }
}
