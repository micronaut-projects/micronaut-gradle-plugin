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
package io.micronaut.gradle.dev;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Writes the manifest {@code io.micronaut.dev.MicronautDevMain} reads: the classpaths, the roots,
 * the compiler options, and the settings of the {@code micronaut.dev} extension, as a properties file
 * beside argument files holding the long classpaths. Everything is a provider, so the task is
 * configuration-cache safe and runs nothing to describe the project.
 *
 * @since 5.0.3
 */
public abstract class MicronautDevManifest extends DefaultTask {

    /**
     * The name of the manifest file.
     */
    public static final String MANIFEST_FILE_NAME = "dev.properties";

    static final String PREFIX = "micronaut.dev.";

    /**
     * @return the application's main class
     */
    @Input
    public abstract Property<String> getMainClass();

    /**
     * @return the project directory, the working directory of the launcher
     */
    @Input
    public abstract Property<String> getProjectDirectory();

    /**
     * @return the reload strategy
     */
    @Input
    public abstract Property<String> getStrategy();

    /**
     * @return the compile mode
     */
    @Input
    public abstract Property<String> getCompileMode();

    /**
     * @return the compile mode of Kotlin sources, when it differs
     */
    @Input
    @Optional
    public abstract Property<String> getKotlinCompileMode();

    /**
     * @return whether compilation is incremental
     */
    @Input
    public abstract Property<Boolean> getIncremental();

    /**
     * @return the types retained across a restart
     */
    @Input
    public abstract ListProperty<String> getRetain();

    /**
     * @return the LiveReload port
     */
    @Input
    public abstract Property<Integer> getLiveReloadPort();

    /**
     * @return whether the LiveReload script is injected
     */
    @Input
    public abstract Property<Boolean> getLiveReloadInjectScript();

    /**
     * @return the runtime classpath: the jars of the parent loader, never the project's own outputs
     */
    @Classpath
    public abstract ConfigurableFileCollection getRuntimeClasspath();

    /**
     * @return the reloadable directories: the project's outputs and those of the projects it depends on, as
     *         paths, since the manifest names them without building them
     */
    @Input
    public abstract ListProperty<String> getReloadableRoots();

    /**
     * @return the compile classpath
     */
    @Classpath
    public abstract ConfigurableFileCollection getCompileClasspath();

    /**
     * @return the annotation and symbol processor path
     */
    @Classpath
    public abstract ConfigurableFileCollection getProcessorPath();

    /**
     * @return the Java source directories
     */
    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public abstract ConfigurableFileCollection getJavaSources();

    /**
     * @return the Kotlin source directories
     */
    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public abstract ConfigurableFileCollection getKotlinSources();

    /**
     * @return the Groovy source directories
     */
    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public abstract ConfigurableFileCollection getGroovySources();

    /**
     * @return the resource source directories, the configuration roots
     */
    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public abstract ConfigurableFileCollection getResourceSources();

    /**
     * @return the class output of the Java compilation, as a path: named in the manifest, not built by it
     */
    @Input
    @Optional
    public abstract Property<String> getJavaOutput();

    /**
     * @return the generated sources output of the Java compilation, as a path
     */
    @Input
    @Optional
    public abstract Property<String> getJavaGeneratedSources();

    /**
     * @return the class output of the Kotlin compilation, as a path
     */
    @Input
    @Optional
    public abstract Property<String> getKotlinOutput();

    /**
     * @return the class output of the Groovy compilation, as a path
     */
    @Input
    @Optional
    public abstract Property<String> getGroovyOutput();

    /**
     * @return the javac options, as the build passes them
     */
    @Input
    public abstract ListProperty<String> getJavaOptions();

    /**
     * @return the kotlinc options, as the build passes them
     */
    @Input
    public abstract ListProperty<String> getKotlinOptions();

    /**
     * @return the groovyc options
     */
    @Input
    public abstract ListProperty<String> getGroovyOptions();

    /**
     * @return the file the build tool touches when it finished compiling, in build-tool mode
     */
    @Input
    public abstract Property<String> getBuildToolTrigger();

    /**
     * @return the manifest file
     */
    @OutputFile
    public abstract RegularFileProperty getManifestFile();

    /**
     * @return the argument files written beside the manifest, which it references with {@code @}
     */
    @org.gradle.api.tasks.OutputFiles
    public abstract ConfigurableFileCollection getArgumentFiles();

    @TaskAction
    void write() {
        File manifest = getManifestFile().get().getAsFile();
        Path directory = manifest.toPath().getParent();
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create " + directory, e);
        }
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put(PREFIX + "main-class", getMainClass().get());
        entries.put(PREFIX + "project-dir", getProjectDirectory().get());
        entries.put(PREFIX + "strategy", getStrategy().get());
        entries.put(PREFIX + "runtime-classpath", "@" + argfile(directory, "runtime.argfile", getRuntimeClasspath().getFiles()));
        entries.put(PREFIX + "reloadable", String.join(File.pathSeparator, getReloadableRoots().get()));
        entries.put(PREFIX + "compile-classpath", "@" + argfile(directory, "compile.argfile", getCompileClasspath().getFiles()));
        entries.put(PREFIX + "processor-path", "@" + argfile(directory, "processors.argfile", getProcessorPath().getFiles()));
        putSources(entries, "java", getJavaSources().getFiles());
        putSources(entries, "kotlin", getKotlinSources().getFiles());
        putSources(entries, "groovy", getGroovySources().getFiles());
        Set<File> resources = existing(getResourceSources().getFiles());
        if (!resources.isEmpty()) {
            entries.put(PREFIX + "resources.config", join(resources));
            // the conventional directories under the resources: typed so that a change there restarts nothing
            putResourceKind(entries, "views", resources, "views");
            putResourceKind(entries, "static", resources, "static", "public");
            putResourceKind(entries, "i18n", resources, "i18n");
        }
        entries.put(PREFIX + "compile.mode", getCompileMode().get());
        entries.put(PREFIX + "compile.incremental", String.valueOf(getIncremental().get()));
        if (getKotlinCompileMode().isPresent()) {
            entries.put(PREFIX + "compile.kotlin.mode", getKotlinCompileMode().get());
        }
        putCompilation(entries, directory, "java", getJavaSources().getFiles(), getJavaOutput(), getJavaGeneratedSources(), getJavaOptions().get());
        putCompilation(entries, directory, "kotlin", getKotlinSources().getFiles(), getKotlinOutput(), null, getKotlinOptions().get());
        putCompilation(entries, directory, "groovy", getGroovySources().getFiles(), getGroovyOutput(), null, getGroovyOptions().get());
        entries.put(PREFIX + "build-tool", "gradle");
        entries.put(PREFIX + "build-tool.trigger", getBuildToolTrigger().get());
        List<String> retain = getRetain().get();
        if (!retain.isEmpty()) {
            entries.put(PREFIX + "retain", String.join(",", retain));
        }
        entries.put(PREFIX + "livereload.port", String.valueOf(getLiveReloadPort().get()));
        entries.put(PREFIX + "livereload.inject-script", String.valueOf(getLiveReloadInjectScript().get()));
        putEntries(entries, directory);
        Properties properties = new Properties();
        properties.putAll(entries);
        try (OutputStream out = Files.newOutputStream(manifest.toPath())) {
            properties.store(out, "Written by the Micronaut Gradle plugin for io.micronaut.dev.MicronautDevMain");
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + manifest, e);
        }
    }

    /**
     * Adds the entries of a subclass, such as those of test mode, before the manifest is written.
     *
     * @param entries the entries
     * @param directory the manifest directory, where argument files go
     */
    void putEntries(Map<String, String> entries, Path directory) {
    }

    static void putSources(Map<String, String> entries, String prefix, String language, Set<File> directories) {
        Set<File> existing = existing(directories);
        if (!existing.isEmpty()) {
            entries.put(prefix + "sources." + language, join(existing));
        }
    }

    private static void putSources(Map<String, String> entries, String language, Set<File> directories) {
        putSources(entries, PREFIX, language, directories);
    }

    private static void putResourceKind(Map<String, String> entries, String kind, Set<File> resources, String... names) {
        Set<File> found = new LinkedHashSet<>();
        for (File resourceDir : resources) {
            for (String name : names) {
                File candidate = new File(resourceDir, name);
                if (candidate.isDirectory()) {
                    found.add(candidate);
                }
            }
        }
        if (!found.isEmpty()) {
            entries.put(PREFIX + "resources." + kind, join(found));
        }
    }

    private static void putCompilation(Map<String, String> entries, Path directory, String language, Set<File> sources, Property<String> output, Property<String> generatedSources, List<String> options) {
        putCompilation(entries, directory, PREFIX, "", language, sources, output, generatedSources, options);
    }

    /**
     * The entries of one compilation under a prefix, {@code micronaut.dev.} or {@code micronaut.dev.test.}, with its
     * options in an argument file whose name starts with another, so that the two compilations of a language keep
     * one file each.
     */
    static void putCompilation(Map<String, String> entries, Path directory, String prefix, String argfilePrefix, String language, Set<File> sources, Property<String> output, Property<String> generatedSources, List<String> options) {
        if (existing(sources).isEmpty() || !output.isPresent()) {
            return;
        }
        entries.put(prefix + "compile." + language + ".output", output.get());
        if (generatedSources != null && generatedSources.isPresent()) {
            entries.put(prefix + "compile." + language + ".generated-sources", generatedSources.get());
        }
        if (!options.isEmpty()) {
            // one option per line: a comma inside an option, as in -Xlint:unchecked,deprecation, survives
            entries.put(prefix + "compile." + language + ".options", "@" + argfile(directory, argfilePrefix + language + "-options.argfile", options));
        }
    }

    static Set<File> existing(Set<File> directories) {
        Set<File> existing = new LinkedHashSet<>();
        for (File directory : directories) {
            if (directory.isDirectory()) {
                existing.add(directory);
            }
        }
        return existing;
    }

    static String join(Set<File> files) {
        List<String> paths = new ArrayList<>(files.size());
        for (File file : files) {
            paths.add(file.getAbsolutePath());
        }
        return String.join(File.pathSeparator, paths);
    }

    static String argfile(Path directory, String name, Set<File> files) {
        List<String> lines = new ArrayList<>(files.size());
        for (File file : files) {
            lines.add(file.getAbsolutePath());
        }
        return argfile(directory, name, lines);
    }

    private static String argfile(Path directory, String name, List<String> lines) {
        Path argfile = directory.resolve(name);
        try {
            Files.write(argfile, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + argfile, e);
        }
        return name;
    }
}
