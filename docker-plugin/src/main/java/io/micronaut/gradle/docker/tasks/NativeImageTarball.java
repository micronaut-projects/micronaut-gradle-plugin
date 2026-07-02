package io.micronaut.gradle.docker.tasks;

import com.google.cloud.tools.jib.api.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.Jib;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.api.TarImage;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import com.google.cloud.tools.jib.api.buildplan.ImageFormat;
import com.google.cloud.tools.jib.api.buildplan.Platform;
import com.google.cloud.tools.jib.api.buildplan.Port;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Packages an already-built native executable into a container image tarball without a Docker daemon.
 */
public abstract class NativeImageTarball extends DefaultTask {
    private static final String DEFAULT_APP_ROOT = "/app";
    private static final String DEFAULT_USER = "65532";
    private static final String LINUX = "linux";
    private static final String AMD64 = "amd64";
    private static final String ARM64 = "arm64";

    /**
     * @return The native executable to copy into the image.
     */
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getExecutable();

    /**
     * @return The output tarball.
     */
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    /**
     * @return The image reference stored in the tarball.
     */
    @Input
    public abstract Property<String> getImageName();

    /**
     * @return The runtime base image.
     */
    @Input
    public abstract Property<String> getBaseImage();

    /**
     * @return The working directory inside the image.
     */
    @Input
    public abstract Property<String> getWorkingDirectory();

    /**
     * @return The runtime user.
     */
    @Input
    public abstract Property<String> getUser();

    /**
     * @return Arguments passed to the native executable.
     */
    @Input
    public abstract ListProperty<String> getArgs();

    /**
     * @return Exposed container ports.
     */
    @Input
    public abstract ListProperty<Integer> getExposedPorts();

    /**
     * @return Target operating system. Native executable images support Linux only.
     */
    @Input
    public abstract Property<String> getTargetOs();

    /**
     * @return Target architecture.
     */
    @Input
    public abstract Property<String> getTargetArchitecture();

    /**
     * @return Whether host/target OS or architecture mismatch is allowed.
     */
    @Input
    public abstract Property<Boolean> getAllowPlatformMismatch();

    public NativeImageTarball() {
        setGroup(BasePlugin.BUILD_GROUP);
        setDescription("Builds a native executable container image tarball without a Docker daemon");
        getBaseImage().convention("cgr.dev/chainguard/wolfi-base:latest");
        getWorkingDirectory().convention(DEFAULT_APP_ROOT);
        getUser().convention(DEFAULT_USER);
        getArgs().convention(List.of());
        getExposedPorts().convention(List.of(8080));
        getTargetOs().convention(LINUX);
        getTargetArchitecture().convention(normalizeArchitecture(System.getProperty("os.arch")));
        getAllowPlatformMismatch().convention(false);
    }

    /**
     * Adds arguments passed to the native executable.
     * @param args The arguments
     */
    public void args(String... args) {
        getArgs().addAll(args);
    }

    @TaskAction
    public void buildTarball() {
        validatePlatform();
        Path executable = getExecutable().get().getAsFile().toPath();
        if (!Files.isRegularFile(executable)) {
            throw new GradleException("Native executable not found: " + executable + ". Run nativeCompile first or configure " + getPath() + ".executable.");
        }
        Path output = getOutputFile().get().getAsFile().toPath();
        try {
            Files.createDirectories(output.getParent());
            String executableName = executable.getFileName().toString();
            AbsoluteUnixPath appRoot = AbsoluteUnixPath.get(getWorkingDirectory().get());
            AbsoluteUnixPath containerExecutable = appRoot.resolve(executableName);
            JibContainerBuilder builder = containerBuilder()
                .addFileEntriesLayer(FileEntriesLayer.builder()
                    .setName("native executable")
                    .addEntry(executable, containerExecutable, FilePermissions.fromOctalString("755"))
                    .build())
                .setEntrypoint(containerExecutable.toString())
                .setProgramArguments(getArgs().get())
                .setExposedPorts(ports())
                .setFormat(ImageFormat.Docker)
                .setPlatforms(Set.of(new Platform(getTargetArchitecture().get(), getTargetOs().get())))
                .setWorkingDirectory(appRoot)
                .setUser(getUser().get());
            builder.containerize(Containerizer.to(TarImage.at(output).named(imageName()))
                .addEventHandler(LogEvent.class, this::logJibEvent)
                .setToolName("micronaut-gradle-plugin"));
            getLogger().lifecycle("Native image container tarball written to: {}", output.toAbsolutePath());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GradleException("Interrupted while building native image container tarball", e);
        } catch (CacheDirectoryCreationException | ExecutionException | IOException | RegistryException e) {
            throw new GradleException("Failed to build native image container tarball with Jib: " + e.getMessage(), e);
        } catch (InvalidImageReferenceException e) {
            throw new GradleException("Invalid image reference for native image container tarball: " + e.getMessage(), e);
        }
    }

    private JibContainerBuilder containerBuilder() throws InvalidImageReferenceException {
        String baseImage = getBaseImage().get();
        if ("scratch".equals(baseImage)) {
            return Jib.fromScratch();
        }
        return Jib.from(baseImage);
    }

    private String imageName() {
        String image = getImageName().get();
        int lastSlash = image.lastIndexOf('/');
        int tagSeparator = image.indexOf(':', lastSlash + 1);
        if (tagSeparator < 0 && !image.contains("@")) {
            return image + ":latest";
        }
        return image;
    }

    private Set<Port> ports() {
        Set<Port> ports = new LinkedHashSet<>();
        for (Integer port : getExposedPorts().get()) {
            ports.add(Port.tcp(port));
        }
        return ports;
    }

    private void validatePlatform() {
        String targetOs = getTargetOs().get();
        String targetArchitecture = getTargetArchitecture().get();
        if (!LINUX.equals(targetOs)) {
            throw new GradleException("Native image tarball packaging supports Linux container images only. Configured platform is "
                + targetOs + "/" + targetArchitecture + ".");
        }
        if (getAllowPlatformMismatch().get()) {
            return;
        }
        String hostOs = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
        if (!hostOs.contains(LINUX)) {
            throw new GradleException("Native image tarball packaging requires a Linux host by default because the local native executable is copied into a Linux container image. "
                + "Use dockerBuildNative for Docker-backed cross-platform builds, or set allowPlatformMismatch=true for a known Linux cross-compiled executable.");
        }
        String hostArchitecture = normalizeArchitecture(System.getProperty("os.arch"));
        if (!hostArchitecture.equals(targetArchitecture)) {
            throw new GradleException("Native image tarball host architecture " + hostArchitecture
                + " does not match configured target architecture " + targetArchitecture
                + ". Set allowPlatformMismatch=true only for a known compatible cross-compiled executable.");
        }
    }

    private static String normalizeArchitecture(String architecture) {
        return switch (architecture) {
            case "x86_64", "x64", AMD64 -> AMD64;
            case "aarch64", ARM64 -> ARM64;
            default -> architecture;
        };
    }

    private void logJibEvent(LogEvent event) {
        switch (event.getLevel()) {
            case ERROR -> getLogger().error(event.getMessage());
            case WARN -> getLogger().warn(event.getMessage());
            case DEBUG -> getLogger().debug(event.getMessage());
            default -> getLogger().lifecycle(event.getMessage());
        }
    }
}
