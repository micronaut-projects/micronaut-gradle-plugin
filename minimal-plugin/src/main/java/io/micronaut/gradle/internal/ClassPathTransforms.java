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

import io.micronaut.gradle.ClassPathTransformOptions;
import io.micronaut.gradle.DefaultVersions;
import io.micronaut.gradle.MicronautExtension;
import io.micronaut.gradle.PluginsHelper;
import org.gradle.api.Project;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.toolchain.JavaToolchainService;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Wires {@link ClassPathTransformTask} into the packaging tasks: the fat JARs and the JVM Docker images. The
 * packaging tasks read what {@link ClassPathTransformOptions} enables lazily, so nothing is rewritten, and no
 * transform task runs, while both steps are off.
 *
 * @since 5.1.0
 */
public final class ClassPathTransforms {

    /** The name of the options, under the {@code micronaut} extension. */
    public static final String EXTENSION_NAME = "classPathTransform";

    /** The transform of the main source set's classes and the runtime class path. */
    public static final String MAIN_TASK_NAME = "transformClassPath";

    /** The transform of the {@code optimizedJitJar} and the runtime class path. */
    public static final String OPTIMIZED_JIT_TASK_NAME = "transformOptimizedJitClassPath";

    /** The configuration that resolves {@code micronaut-aot-bytecode}. */
    public static final String CONFIGURATION_NAME = "micronautClassPathTransform";

    private ClassPathTransforms() {
    }

    /**
     * Finds or creates the options of a project.
     *
     * @param project the project
     * @return the options
     */
    public static ClassPathTransformOptions options(Project project) {
        MicronautExtension micronaut = project.getExtensions().getByType(MicronautExtension.class);
        ClassPathTransformOptions options = micronaut.getExtensions().findByType(ClassPathTransformOptions.class);
        if (options == null) {
            options = micronaut.getExtensions().create(EXTENSION_NAME, ClassPathTransformOptions.class);
            options.getDesugarLambdas().convention(false);
            options.getStripLocalVariables().convention(false);
            options.getVersion().convention(DefaultVersions.AOT_BYTECODE);
        }
        return options;
    }

    /**
     * Whether a step is enabled.
     *
     * @param project the project
     * @return true when the class path is rewritten
     */
    public static Provider<Boolean> enabled(Project project) {
        ClassPathTransformOptions options = options(project);
        return options.getDesugarLambdas().zip(options.getStripLocalVariables(), (desugar, strip) -> desugar || strip);
    }

    /**
     * Finds or registers the transform of the main source set's classes and the runtime class path.
     *
     * @param project the project
     * @return the transform task
     */
    public static TaskProvider<ClassPathTransformTask> main(Project project) {
        if (project.getTasks().getNames().contains(MAIN_TASK_NAME)) {
            return project.getTasks().named(MAIN_TASK_NAME, ClassPathTransformTask.class);
        }
        SourceSet main = PluginsHelper.findSourceSets(project).getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        return register(project, MAIN_TASK_NAME, main.getOutput().getClassesDirs());
    }

    /**
     * Registers a transform of an application's output, then the runtime class path.
     *
     * @param project the project
     * @param name the task name
     * @param application the application's output: its classes directories, or a JAR
     * @return the transform task
     */
    public static TaskProvider<ClassPathTransformTask> register(Project project, String name, Object application) {
        ClassPathTransformOptions options = options(project);
        Configuration transformClasspath = transformClasspath(project, options);
        Configuration runtimeClasspath = project.getConfigurations().getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);
        FileCollection thirdPartyJars = runtimeClasspath.getIncoming().artifactView(view -> {
            view.lenient(true);
            view.componentFilter(ModuleComponentIdentifier.class::isInstance);
        }).getFiles();
        JavaToolchainService toolchains = project.getExtensions().getByType(JavaToolchainService.class);
        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        return project.getTasks().register(name, ClassPathTransformTask.class, task -> {
            task.setDescription("Rewrites the packaged class path with Micronaut AOT's micronaut-aot-bytecode");
            task.getApplication().from(application);
            task.getDependencies().from(runtimeClasspath);
            task.getThirdPartyJars().from(thirdPartyJars);
            task.getTransformClasspath().from(transformClasspath);
            task.getDesugarLambdas().convention(options.getDesugarLambdas());
            task.getStripLocalVariables().convention(options.getStripLocalVariables());
            task.getJavaLauncher().convention(toolchains.launcherFor(java.getToolchain()));
            task.getOutputDirectory().convention(project.getLayout().getBuildDirectory().dir("micronaut/" + name));
        });
    }

    private static Configuration transformClasspath(Project project, ClassPathTransformOptions options) {
        Configuration existing = project.getConfigurations().findByName(CONFIGURATION_NAME);
        if (existing != null) {
            return existing;
        }
        return project.getConfigurations().create(CONFIGURATION_NAME, conf -> {
            conf.setCanBeConsumed(false);
            conf.setCanBeResolved(true);
            conf.setDescription("The class path of Micronaut AOT's micronaut-aot-bytecode, which rewrites the packaged class path");
            conf.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
            conf.getDependencies().addLater(options.getVersion().map(version ->
                project.getDependencies().create("io.micronaut.aot:micronaut-aot-bytecode:" + version)));
        });
    }

    /**
     * What a transform packages in place of each entry of its class path.
     *
     * @param project the project
     * @param transform the transform task
     * @return each entry, mapped to its rewritten copy or to itself
     */
    public static Provider<Map<File, File>> mapping(Project project, TaskProvider<ClassPathTransformTask> transform) {
        return transform.flatMap(task -> task.getOutputDirectory().file(ClassPathTransformTask.CLASS_PATH_FILE))
            .map(new ReadMapping());
    }

    /**
     * The files to package in place of the given ones: their rewritten copies when a step is enabled.
     *
     * @param project the project
     * @param transform the transform task, whose class path holds the files
     * @param original the files
     * @return the files to package
     */
    public static FileCollection packaged(Project project, TaskProvider<ClassPathTransformTask> transform, FileCollection original) {
        Provider<Boolean> enabled = enabled(project);
        Provider<Map<File, File>> mapping = mapping(project, transform);
        ConfigurableFileCollection packaged = project.getObjects().fileCollection();
        packaged.from((Callable<Object>) () -> {
            if (!Boolean.TRUE.equals(enabled.get())) {
                return original;
            }
            return original.getElements().zip(mapping, new Substitute());
        });
        return packaged;
    }

    /**
     * The file to package in place of the given one: its rewritten copy when a step is enabled.
     *
     * @param project the project
     * @param transform the transform task, whose class path holds the file
     * @param original the file
     * @return the file to package
     */
    public static Provider<File> packagedFile(Project project, TaskProvider<ClassPathTransformTask> transform, Provider<RegularFile> original) {
        Provider<Map<File, File>> mapping = mapping(project, transform);
        return enabled(project).flatMap(on -> {
            if (Boolean.TRUE.equals(on)) {
                return original.zip(mapping, (file, map) -> map.getOrDefault(file.getAsFile(), file.getAsFile()));
            }
            return original.map(RegularFile::getAsFile);
        });
    }

    private static Map<File, File> read(File file) {
        try {
            Map<File, File> mapping = new LinkedHashMap<>();
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                int tab = line.indexOf('\t');
                if (tab > 0) {
                    mapping.put(new File(line.substring(0, tab)), new File(line.substring(tab + 1)));
                }
            }
            return mapping;
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read the rewritten class path from " + file, e);
        }
    }

    private static final class ReadMapping implements Transformer<Map<File, File>, RegularFile>, Serializable {
        @Override
        public Map<File, File> transform(RegularFile file) {
            return read(file.getAsFile());
        }
    }

    private static final class Substitute implements java.util.function.BiFunction<Set<FileSystemLocation>, Map<File, File>, List<File>>, Serializable {
        @Override
        public List<File> apply(Set<FileSystemLocation> elements, Map<File, File> mapping) {
            List<File> files = new ArrayList<>(elements.size());
            for (FileSystemLocation element : elements) {
                File file = element.getAsFile();
                files.add(mapping.getOrDefault(file, file));
            }
            return files;
        }
    }
}
