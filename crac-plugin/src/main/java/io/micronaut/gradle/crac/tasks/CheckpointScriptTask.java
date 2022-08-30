package io.micronaut.gradle.crac.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;

@CacheableTask
public abstract class CheckpointScriptTask extends DefaultTask {

    private static final EnumSet<PosixFilePermission> POSIX_FILE_PERMISSIONS = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_EXECUTE,
            PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE);

    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getCheckpointFile();

    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getWarmupFile();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    @TaskAction
    void perform() throws IOException {
        Provider<RegularFile> checkpointFile = getOutputDir().file("checkpoint.sh");
        Path checkpointScriptPath = checkpointFile.get().getAsFile().toPath();
        if (!getCheckpointFile().isPresent()) {
            Files.copy(CheckpointScriptTask.class.getResourceAsStream("/checkpoint.sh"), checkpointScriptPath, StandardCopyOption.REPLACE_EXISTING);
        } else {
            Files.copy(getCheckpointFile().get().getAsFile().toPath(), checkpointScriptPath, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.setPosixFilePermissions(checkpointScriptPath, POSIX_FILE_PERMISSIONS);

        Provider<RegularFile> warmupFile = getOutputDir().file("warmup.sh");
        Path warmupScriptPath = warmupFile.get().getAsFile().toPath();
        if (!getCheckpointFile().isPresent()) {
            Files.copy(CheckpointScriptTask.class.getResourceAsStream("/warmup.sh"), warmupScriptPath, StandardCopyOption.REPLACE_EXISTING);
        } else {
            Files.copy(getWarmupFile().get().getAsFile().toPath(), warmupScriptPath, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.setPosixFilePermissions(warmupScriptPath, POSIX_FILE_PERMISSIONS);
    }
}
