package io.micronaut.gradle.buildpacks.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;

/**
 * Prepares a directory consumed by the pack CLI.
 */
public abstract class PrepareBuildpackApplicationTask extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getApplicationJar();

    @Classpath
    public abstract ConfigurableFileCollection getRuntimeClasspath();

    @Optional
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getResources();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Inject
    protected abstract FileOperations getFileOperations();

    @TaskAction
    public void prepareApplication() {
        var outputDirectory = getOutputDirectory();
        getFileOperations().delete(outputDirectory);
        getFileOperations().copy(copy -> {
            copy.from(getApplicationJar());
            copy.into(outputDirectory);
            copy.rename(fileName -> "application.jar");
        });
        getFileOperations().copy(copy -> {
            copy.from(getRuntimeClasspath());
            copy.into(outputDirectory.dir("libs"));
        });
        getFileOperations().copy(copy -> {
            copy.from(getResources());
            copy.into(outputDirectory.dir("resources"));
        });
    }
}
