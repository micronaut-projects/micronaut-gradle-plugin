package io.micronaut.gradle.docker.tasks;

import io.micronaut.gradle.docker.model.Layer;
import io.micronaut.gradle.docker.model.LayerKind;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;

@CacheableTask
public abstract class BuildLayersTask extends DefaultTask {

    @Nested
    public abstract ListProperty<Layer> getLayers();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    @Inject
    protected abstract FileOperations getFileOperations();

    @TaskAction
    public void action() {
        FileOperations fileOperations = getFileOperations();
        fileOperations.delete(getOutputDir());
        // Create folders if case there are no resources/libs in project
        Provider<Directory> libsDir = getOutputDir().dir("libs");
        fileOperations.mkdir(libsDir);
        Provider<Directory> resourcesDir = getOutputDir().dir("resources");
        fileOperations.mkdir(resourcesDir);
        Provider<Directory> classesDir = getOutputDir().dir("classes");
        fileOperations.mkdir(classesDir);
        for (Layer layer : getLayers().get()) {
            final Provider<Directory> layerDir = layerDirectoryOf(layer, getOutputDir(), libsDir, resourcesDir, classesDir);
            if (layer.getLayerKind().get() == LayerKind.APP) {
                // special case for now
                fileOperations.copy(copy -> copy.from(layer.getFiles()).into(getOutputDir()).rename(s -> "application.jar"));
            } else {
                fileOperations.copy(copy -> copy.from(layer.getFiles()).into(layerDir));
            }
        }
    }

    private static Provider<Directory> layerDirectoryOf(Layer layer,
                                                        Provider<Directory> appDir,
                                                        Provider<Directory> libsDir,
                                                        Provider<Directory> resourcesDir,
                                                        Provider<Directory> classesDir) {
        Provider<Directory> layerDir = libsDir;
        switch (layer.getLayerKind().get()) {
            case APP:
                layerDir = appDir;
                break;
            case LIBS:
                layerDir = libsDir;
                break;
            case EXPANDED_CLASSES:
                layerDir = classesDir;
                break;
            case EXPANDED_RESOURCES:
                layerDir = resourcesDir;
                break;
        }
        return layerDir;
    }
}
