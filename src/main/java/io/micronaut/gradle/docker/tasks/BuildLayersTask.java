package io.micronaut.gradle.docker.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;

public class BuildLayersTask extends DefaultTask {
    private final ConfigurableFileCollection libsLayer;
    private final ConfigurableFileCollection resourcesLayer;
    private final ConfigurableFileCollection appLayer;
    private final DirectoryProperty outputDir;
    private final FileOperations fileOperations;

    @Inject
    public BuildLayersTask(ObjectFactory objectFactory, FileOperations fileOperations) {
        this.libsLayer = objectFactory.fileCollection();
        this.resourcesLayer = objectFactory.fileCollection();
        this.appLayer = objectFactory.fileCollection();
        this.outputDir = objectFactory.directoryProperty();
        this.outputDir.set(getProject().getLayout().getBuildDirectory().dir("docker/layers"));
        this.fileOperations = fileOperations;
    }

    @TaskAction
    public void action() {
        fileOperations.delete(outputDir);
        // Create folders if case there are no resources/libs in project
        Provider<RegularFile> libsDir = outputDir.file("libs");
        fileOperations.mkdir(libsDir);
        Provider<RegularFile> resourcesDir = outputDir.file("resources");
        fileOperations.mkdir(resourcesDir);
        fileOperations.copy(copy -> copy.from(libsLayer).into(libsDir));
        fileOperations.copy(copy -> copy.from(appLayer).into(outputDir).rename(s -> "application.jar"));
        fileOperations.copy(copy -> copy.from(resourcesLayer).into(resourcesDir));
    }


    @InputFiles
    public ConfigurableFileCollection getLibsLayer() {
        return libsLayer;
    }

    @InputFiles
    public ConfigurableFileCollection getResourcesLayer() {
        return resourcesLayer;
    }

    @InputFiles
    public ConfigurableFileCollection getAppLayer() {
        return appLayer;
    }

    @OutputDirectory
    public DirectoryProperty getOutputDir() {
        return outputDir;
    }
}
