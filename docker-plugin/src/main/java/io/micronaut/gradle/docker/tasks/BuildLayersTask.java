package io.micronaut.gradle.docker.tasks;

import io.micronaut.gradle.docker.model.Layer;
import io.micronaut.gradle.docker.model.LayerKind;
import org.gradle.api.DefaultTask;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@CacheableTask
public abstract class BuildLayersTask extends DefaultTask {

    @Nested
    public abstract ListProperty<Layer> getLayers();

    @Input
    @Optional
    public abstract Property<DuplicatesStrategy> getDuplicatesStrategy();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    @Inject
    protected abstract FileOperations getFileOperations();

    @TaskAction
    public void action() {
        FileOperations fileOperations = getFileOperations();
        fileOperations.delete(getOutputDir());
        // Create folders if case there are no resources/libs in project
        for (Layer layer : getLayers().get()) {
            final Provider<Directory> layerDir = layerDirectoryOf(layer, getOutputDir());
            if (layer.getLayerKind().get() == LayerKind.APP) {
                // special case for now
                copyLayer(fileOperations, layer, getOutputDir().dir("app"), copy -> copy.rename(s -> "application.jar"));
            } else {
                copyLayer(fileOperations, layer, layerDir, copy -> {
                });
            }
        }
    }

    private void copyLayer(FileOperations fileOperations,
                           Layer layer,
                           Provider<Directory> destination,
                           org.gradle.api.Action<CopySpec> customizer) {
        Path destinationPath = destination.get().getAsFile().toPath();
        Map<Path, Path> copiedFiles = new LinkedHashMap<>();
        fileOperations.copy(copy -> {
            configureDuplicatesStrategy(copy);
            copy.from(layer.getFiles()).into(destination);
            customizer.execute(copy);
            copy.eachFile(details -> copiedFiles.put(destinationPath.resolve(relativePathOf(details)), details.getFile().toPath()));
        });
        restoreLastModifiedTimes(copiedFiles);
    }

    private Path relativePathOf(org.gradle.api.file.FileCopyDetails details) {
        String[] segments = details.getRelativePath().getSegments();
        if (segments.length == 0) {
            return Path.of("");
        }
        return Path.of(segments[0], java.util.Arrays.copyOfRange(segments, 1, segments.length));
    }

    private void restoreLastModifiedTimes(Map<Path, Path> copiedFiles) {
        for (Map.Entry<Path, Path> entry : copiedFiles.entrySet()) {
            try {
                if (Files.exists(entry.getKey())) {
                    Files.setLastModifiedTime(entry.getKey(), Files.getLastModifiedTime(entry.getValue()));
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to restore mtime from " + entry.getValue() + " to " + entry.getKey(), e);
            }
        }
    }

    private void configureDuplicatesStrategy(CopySpec copy) {
        if (getDuplicatesStrategy().isPresent()) {
            copy.setDuplicatesStrategy(getDuplicatesStrategy().get());
        }
    }

    private static Provider<Directory> layerDirectoryOf(Layer layer,
                                                        DirectoryProperty outputDir) {
        var kind = layer.getLayerKind().get();
        var dir = outputDir.dir(kind.sourceDirName());
        try {
            Files.createDirectories(dir.get().getAsFile().toPath());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create layer directory " + dir.get().getAsFile(), e);
        }
        return dir;
    }
}
