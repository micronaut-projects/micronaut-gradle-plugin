package io.micronaut.gradle.docker.tasks;

import io.micronaut.gradle.docker.model.Layer;
import io.micronaut.gradle.docker.model.LayerKind;
import org.gradle.api.DefaultTask;
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
                fileOperations.copy(copy -> {
                    configureDuplicatesStrategy(copy);
                    copy.from(layer.getFiles()).into(getOutputDir().dir("app")).rename(s -> "application.jar");
                });
            } else {
                fileOperations.copy(copy -> {
                    configureDuplicatesStrategy(copy);
                    copy.from(layer.getFiles()).into(layerDir);
                });
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
            throw new RuntimeException(e);
        }
        return dir;
    }
}
