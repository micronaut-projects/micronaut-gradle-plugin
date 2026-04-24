package io.micronaut.gradle.docker;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
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

    public DockerBuildx() {
        setGroup(BasePlugin.BUILD_GROUP);
        getDockerExecutable().convention("docker");
        getPlatforms().convention(List.of());
        getImages().convention(List.of());
    }

    @Input
    public abstract ListProperty<String> getPlatforms();

    @Input
    public abstract ListProperty<String> getImages();

    @Input
    @Optional
    public abstract Property<String> getBuilder();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getDockerFile();

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getInputDir();

    @Internal
    public abstract Property<String> getDockerExecutable();

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
        List<String> trimmedValues = trimmedValuesOf(values);
        if (trimmedValues.isEmpty()) {
            throw new GradleException("Property '" + propertyName + "' must not be empty.");
        }
        if (trimmedValues.size() != values.size()) {
            throw new GradleException("Property '" + propertyName + "' must not contain blank values.");
        }
    }

    private static List<String> trimmedValuesOf(List<String> values) {
        return values.stream()
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .toList();
    }
}
