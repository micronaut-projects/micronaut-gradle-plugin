/*
 * Copyright 2003-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.gradle;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.JavaExec;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.JavaLauncher;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * The first action of the {@code run} task when {@link RunClassDataSharing} is enabled. It looks at the
 * task as it is about to launch (class path, JVM arguments, environment, launcher), and changes its class
 * path order and JVM arguments to use the CDS archive of the dependency JARs.
 */
final class RunClassDataSharingAction implements Action<Task> {

    /**
     * The oldest launcher that the setting acts on.
     */
    static final int MINIMUM_JAVA_VERSION = 25;

    /**
     * The newest launcher with which the archive may be AOT-linked: an AOT-linked dump fails on JDK 27.
     */
    static final int LAST_AOT_LINKING_JAVA_VERSION = 26;

    private final Provider<Boolean> enabled;
    private final Provider<Boolean> aotClassLinking;
    private final Provider<Set<ResolvedArtifactResult>> dependencies;
    private final Provider<Directory> directory;

    /**
     * Creates the action.
     *
     * @param enabled whether the setting is enabled
     * @param aotClassLinking whether AOT class linking is requested
     * @param dependencies the resolved artifacts of the run task's runtime class path configuration
     * @param directory the directory of the archives
     */
    RunClassDataSharingAction(Provider<Boolean> enabled,
                              Provider<Boolean> aotClassLinking,
                              Provider<Set<ResolvedArtifactResult>> dependencies,
                              Provider<Directory> directory) {
        this.enabled = enabled;
        this.aotClassLinking = aotClassLinking;
        this.dependencies = dependencies;
        this.directory = directory;
    }

    @Override
    public void execute(Task task) {
        if (!enabled.getOrElse(false)) {
            return;
        }
        var run = (JavaExec) task;
        Logger logger = run.getLogger();
        long start = System.nanoTime();
        JavaLauncher launcher = run.getJavaLauncher().getOrNull();
        if (launcher == null) {
            logger.warn("The run task has no Java launcher, so it starts without a CDS archive of its dependencies.");
            return;
        }
        JavaInstallationMetadata jdk = launcher.getMetadata();
        int javaVersion = jdk.getLanguageVersion().asInt();
        if (javaVersion < MINIMUM_JAVA_VERSION) {
            logger.warn("A CDS archive of the dependencies of the run task needs JDK {} or later, and the run task's launcher is JDK {}: it starts without one.",
                MINIMUM_JAVA_VERSION, javaVersion);
            return;
        }
        if (run.getMainModule().isPresent()) {
            logger.info("The run task launches a main module, so it starts without a CDS archive of its dependencies.");
            return;
        }
        Map<String, String> environment = environment(run.getEnvironment());
        RunCdsJvmOptions options = RunCdsJvmOptions.of(run.getAllJvmArgs(), environment, jdk.getJavaRuntimeVersion());
        if (options.configuresClassDataSharing()) {
            logger.info("The run task configures class data sharing itself, so it starts without a CDS archive of its dependencies.");
            return;
        }

        if (!(run.getClasspath() instanceof ConfigurableFileCollection classpath)) {
            logger.info("The class path of the run task cannot be reordered, so it starts without a CDS archive of its dependencies.");
            return;
        }
        Set<File> moduleJars = moduleJars();
        var archived = new ArrayList<File>();
        var changing = new ArrayList<File>();
        for (File entry : classpath.getFiles()) {
            (moduleJars.contains(entry) ? archived : changing).add(entry);
        }
        if (archived.isEmpty()) {
            logger.info("The run task has no dependency JAR to archive.");
            return;
        }
        File root = directory.get().getAsFile();
        if (root.getAbsolutePath().indexOf(File.pathSeparatorChar) >= 0) {
            // the JVM would read -XX:SharedArchiveFile as a list of archives
            logger.warn("The build directory {} contains '{}', so the run task starts without a CDS archive of its dependencies.",
                root, File.pathSeparator);
            return;
        }

        try {
            var archive = new RunCdsArchive(root.toPath(), archived, launcher.getExecutablePath().getAsFile().toPath(),
                jdk.getInstallationPath().getAsFile().toPath(), jdk.getJavaRuntimeVersion(), environment, logger);
            long duplicateCheck = System.nanoTime();
            List<String> duplicates = archive.findDuplicates(changing);
            long duplicateCheckMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - duplicateCheck);
            if (!duplicates.isEmpty()) {
                archive.logDuplicates(duplicates);
                return;
            }
            var launchClasspath = new ArrayList<File>(archived);
            launchClasspath.addAll(changing);
            classpath.setFrom(launchClasspath);

            RunCdsArchive.Mode mode = RunCdsArchive.Mode.PLAIN;
            if (aotClassLinking.getOrElse(false)) {
                String blocker = javaVersion > LAST_AOT_LINKING_JAVA_VERSION
                    ? "the launcher is JDK " + javaVersion
                    : options.aotClassLinkingBlocker();
                if (blocker == null) {
                    mode = RunCdsArchive.Mode.LINKED;
                } else {
                    logger.info("The CDS archive of the run task is not AOT-linked, because {}.", blocker);
                }
            }
            run.jvmArgs(archive.launchArguments(mode, options, launchClasspath));
            logger.info("Prepared class data sharing for {} dependency JARs in {} ms (duplicate check of {} class path entries: {} ms)",
                archived.size(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start), changing.size(), duplicateCheckMillis);
        } catch (InterruptedIOException e) {
            // the build was cancelled during a dump or a probe
            throw new GradleException(e.getMessage(), e);
        } catch (IOException e) {
            logger.warn("The run task could not prepare the CDS archive of its dependencies ({}), so it starts without one.", e.getMessage());
            logger.debug("CDS archive failure", e);
        }
    }

    /**
     * The JAR files of external modules on the runtime class path configuration, which go into the
     * archive. Project dependencies, file dependencies and anything else change between runs.
     */
    private Set<File> moduleJars() {
        var jars = new HashSet<File>();
        for (ResolvedArtifactResult artifact : dependencies.get()) {
            File file = artifact.getFile();
            if (artifact.getId().getComponentIdentifier() instanceof ModuleComponentIdentifier
                && file.isFile()
                && file.getName().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                jars.add(file);
            }
        }
        return jars;
    }

    private static Map<String, String> environment(Map<String, Object> environment) {
        var result = new LinkedHashMap<String, String>();
        environment.forEach((name, value) -> {
            if (value != null) {
                result.put(name, value.toString());
            }
        });
        return result;
    }
}
