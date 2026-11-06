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

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar;
import io.micronaut.gradle.PluginsHelper;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;
import java.util.stream.Stream;

/**
 * Packages the rewritten class path of {@link ClassPathTransforms} into Shadow tasks. It is separate from
 * {@link ClassPathTransforms} so that the Shadow classes load only when the Shadow plugin is applied.
 *
 * @since 5.1.0
 */
public final class ClassPathTransformShadowSupport {

    private ClassPathTransformShadowSupport() {
    }

    /**
     * Packages the rewritten class path into the main {@code shadowJar}: the rewritten copies of the
     * application's classes replace the originals, and the rewritten dependency JARs replace the originals
     * that the Shadow configurations and dependency filter include.
     *
     * @param project the project
     */
    public static void configureMainShadowJar(Project project) {
        TaskProvider<ClassPathTransformTask> transform = ClassPathTransforms.main(project);
        SourceSet main = PluginsHelper.findSourceSets(project).getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        FileCollection classesDirs = main.getOutput().getClassesDirs();
        Provider<Boolean> enabled = ClassPathTransforms.enabled(project);
        Provider<Map<File, File>> mapping = ClassPathTransforms.mapping(project, transform);
        project.getTasks().withType(ShadowJar.class).named(name -> name.equals("shadowJar")).configureEach(shadow -> {
            shadow.from((Callable<Object>) () -> Boolean.TRUE.equals(enabled.get())
                ? classesDirs.getElements().zip(mapping, new RewrittenOnly())
                : List.of());
            shadow.exclude(new ReplacedOriginals(enabled, classesDirs, mapping));
            configureShadowDependencies(project, shadow, transform);
        });
    }

    /**
     * Replaces the dependencies that a Shadow task includes with their rewritten copies.
     *
     * @param project the project
     * @param shadow the Shadow task
     * @param transform the transform task, whose class path holds the dependencies
     */
    public static void configureShadowDependencies(Project project, ShadowJar shadow, TaskProvider<ClassPathTransformTask> transform) {
        FileCollection included = project.getObjects().fileCollection()
            .from(shadow.getDependencyFilter().zip(shadow.getConfigurations(), (filter, configurations) -> filter.resolve(configurations)));
        shadow.getIncludedDependencies().setFrom(ClassPathTransforms.packaged(project, transform, included));
    }

    private static final class RewrittenOnly implements BiFunction<Set<FileSystemLocation>, Map<File, File>, List<File>>, Serializable {
        @Override
        public List<File> apply(Set<FileSystemLocation> elements, Map<File, File> mapping) {
            List<File> files = new ArrayList<>();
            for (FileSystemLocation element : elements) {
                File copy = mapping.get(element.getAsFile());
                if (copy != null && !copy.equals(element.getAsFile())) {
                    files.add(copy);
                }
            }
            return files;
        }
    }

    /**
     * Excludes the files of the application's classes directories that a rewritten copy replaces. Only a file
     * whose path a copy holds is checked against the originals, so the entries of the dependency JARs are not
     * extracted.
     */
    private static final class ReplacedOriginals implements Spec<FileTreeElement>, Serializable {
        private final Provider<Boolean> enabled;
        private final FileCollection classesDirs;
        private final Provider<Map<File, File>> mapping;
        private transient List<Path> replacedDirs;
        private transient Set<String> replacedPaths;

        private ReplacedOriginals(Provider<Boolean> enabled, FileCollection classesDirs, Provider<Map<File, File>> mapping) {
            this.enabled = enabled;
            this.classesDirs = classesDirs;
            this.mapping = mapping;
        }

        @Override
        public boolean isSatisfiedBy(FileTreeElement element) {
            if (element.isDirectory() || !Boolean.TRUE.equals(enabled.get())) {
                return false;
            }
            if (replacedPaths == null) {
                collect();
            }
            if (!replacedPaths.contains(element.getRelativePath().getPathString())) {
                return false;
            }
            Path file = element.getFile().toPath().toAbsolutePath();
            return replacedDirs.stream().anyMatch(file::startsWith);
        }

        private void collect() {
            Map<File, File> map = mapping.get();
            List<Path> dirs = new ArrayList<>();
            Set<String> paths = new HashSet<>();
            for (File dir : classesDirs) {
                File copy = map.get(dir);
                if (copy != null && !copy.equals(dir)) {
                    dirs.add(dir.toPath().toAbsolutePath());
                    paths.addAll(relativeFiles(copy.toPath()));
                }
            }
            replacedDirs = dirs;
            replacedPaths = paths;
        }

        private static Collection<String> relativeFiles(Path root) {
            try (Stream<Path> files = Files.walk(root)) {
                return files.filter(Files::isRegularFile)
                    .map(file -> root.relativize(file).toString().replace(File.separatorChar, '/'))
                    .toList();
            } catch (IOException e) {
                throw new UncheckedIOException("Unable to list " + root, e);
            }
        }
    }
}
