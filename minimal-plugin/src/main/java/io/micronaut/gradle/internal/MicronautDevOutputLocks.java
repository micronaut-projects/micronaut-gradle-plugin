/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.gradle.internal;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Internal to the plugin, not an API: the locks of a project's class outputs that {@code micronaut-dev} takes, held by
 * the build while it compiles them. Development mode and test mode, {@code mnDev} beside {@code mnTest}, compile the
 * application into the outputs Gradle compiles it into, and each of their compilations, from reading the outputs to
 * promoting what it produced, holds a {@link FileChannel#lock() file lock} on a file beside the first output,
 * {@code <output>.micronaut-dev.lock}. The {@code mnDevLockOutputs} task takes the same locks before the compile tasks
 * of the main and test source sets and {@code clean} run, so that neither Gradle's check of their outputs, its removal
 * of stale ones, a compilation, an output restored from the build cache nor a clean meets a development process half
 * done, and {@code mnDevUnlockOutputs}, their finalizer, releases them; the launch of {@code mnDev} and {@code mnTest},
 * whose JVM takes them, releases them too, and so does the end of the build.
 *
 * <p>Gradle cannot know which output the launcher picks, the first by path of those it compiles: it locks one file
 * beside each class output of the two source sets, the launcher's among them, in the order of their paths, so that two
 * builds taking them wait for each other without deadlock.</p>
 *
 * @since 5.0.3
 */
public abstract class MicronautDevOutputLocks implements BuildService<BuildServiceParameters.None>, AutoCloseable {

    /**
     * The name of the shared build service.
     */
    public static final String SERVICE_NAME = "micronautDevOutputLocks";

    /**
     * The suffix the launcher gives the lock file beside an output.
     */
    public static final String LOCK_FILE_SUFFIX = ".micronaut-dev.lock";

    private static final Logger LOGGER = Logging.getLogger(MicronautDevOutputLocks.class);

    private final Map<Path, Held> held = new ConcurrentHashMap<>();

    /**
     * The lock file of a class output, as the launcher names it.
     *
     * @param output the class output
     * @return the lock file beside it
     */
    public static Path lockFile(File output) {
        Path path = output.toPath().toAbsolutePath().normalize();
        return path.resolveSibling(path.getFileName() + LOCK_FILE_SUFFIX);
    }

    /**
     * The lock files of class outputs, in the order they are locked.
     *
     * @param outputs the class outputs
     * @return the lock files, sorted by path
     */
    public static List<Path> lockFiles(Collection<File> outputs) {
        TreeSet<Path> files = new TreeSet<>();
        for (File output : outputs) {
            files.add(lockFile(output));
        }
        return new ArrayList<>(files);
    }

    /**
     * Takes the locks, in the order of their paths, waiting for a development process that holds one. A lock this
     * build holds already is kept, unless its file was deleted or replaced since, by {@code clean}: the file at the
     * path is then locked, and the old one released after.
     *
     * @param files the lock files
     */
    public void lock(Collection<Path> files) {
        for (Path file : new TreeSet<>(files)) {
            Held current = held.get(file);
            if (current != null && current.isAt(file)) {
                continue;
            }
            held.put(file, open(file));
            if (current != null) {
                current.release(file);
            }
        }
    }

    /**
     * Releases the locks this build holds of the given files.
     *
     * @param files the lock files
     */
    public void unlock(Collection<Path> files) {
        for (Path file : files) {
            Held current = held.remove(file);
            if (current != null) {
                current.release(file);
            }
        }
    }

    @Override
    public void close() {
        // a build that failed or was cancelled between the two tasks still lets the development processes go
        unlock(List.copyOf(held.keySet()));
    }

    private static Held open(Path file) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            boolean waited = false;
            while (true) {
                FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                try {
                    // the identity of the file opened, read before the wait: a clean meanwhile replaces the file at the path
                    Object opened = fileKey(file);
                    FileLock taken = channel.tryLock();
                    if (taken == null) {
                        if (!waited) {
                            LOGGER.lifecycle("Development mode or test mode of this project is writing its class output: waiting for it ({})", file);
                            waited = true;
                        }
                        channel.lock();
                    }
                    Object locked = fileKey(file);
                    if (locked != null && Objects.equals(opened, locked)) {
                        return new Held(channel, locked);
                    }
                    if (opened == null && locked == null && Files.exists(file)) {
                        // a file system without file identities: the path is all there is to check
                        return new Held(channel, null);
                    }
                    // the file locked is no longer the one at the path: lock that one instead
                    channel.close();
                } catch (IOException | RuntimeException e) {
                    channel.close();
                    throw e;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot lock " + file, e);
        }
    }

    private static Object fileKey(Path file) {
        try {
            return Files.readAttributes(file, BasicFileAttributes.class).fileKey();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * A lock held, with the identity of the file it locks where the file system has one.
     */
    private record Held(FileChannel channel, Object key) {

        boolean isAt(Path file) {
            if (!Files.exists(file)) {
                return false;
            }
            return key == null || Objects.equals(key, fileKey(file));
        }

        void release(Path file) {
            try {
                // closing the channel releases its lock
                channel.close();
            } catch (IOException e) {
                LOGGER.debug("Cannot release the output lock {}", file, e);
            }
        }
    }

    /**
     * Takes or releases the locks of the project's class outputs: {@code mnDevLockOutputs} before the compile tasks,
     * {@code mnDevUnlockOutputs} after them.
     */
    public abstract static class LockTask extends DefaultTask {

        /**
         * @return the service holding the locks
         */
        @Internal
        public abstract Property<MicronautDevOutputLocks> getLocks();

        /**
         * @return the class outputs whose lock files are taken or released
         */
        @Internal
        public abstract ListProperty<String> getClassOutputs();

        /**
         * @return whether the task releases the locks rather than takes them
         */
        @Internal
        public abstract Property<Boolean> getRelease();

        @TaskAction
        void run() {
            new LockAction(getLocks(), getClassOutputs(), getRelease().getOrElse(false)).execute(this);
        }
    }

    /**
     * Takes or releases the locks as an action of another task: released before the launch of {@code mnDev} and
     * {@code mnTest}, whose JVM takes them while the build waits for it, and taken again after {@code clean}, which
     * deleted the files the build locked.
     */
    public static final class LockAction implements Action<Task> {
        private final Provider<MicronautDevOutputLocks> locks;
        private final Provider<List<String>> outputs;
        private final boolean release;

        /**
         * @param locks the service holding the locks
         * @param outputs the class outputs
         * @param release whether the action releases the locks rather than takes them
         */
        public LockAction(Provider<MicronautDevOutputLocks> locks, Provider<List<String>> outputs, boolean release) {
            this.locks = locks;
            this.outputs = outputs;
            this.release = release;
        }

        @Override
        public void execute(Task task) {
            List<Path> files = lockFiles(outputs.get().stream().map(File::new).toList());
            if (release) {
                locks.get().unlock(files);
            } else {
                locks.get().lock(files);
            }
        }
    }
}
