/*
 * Copyright 2003-2022 the original author or authors.
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
package io.micronaut.gradle.logback;

import io.micronaut.gradle.MicronautBasePlugin;
import io.micronaut.gradle.MicronautExtension;
import io.micronaut.gradle.PluginsHelper;
import io.micronaut.gradle.aot.AOTExtension;
import org.graalvm.buildtools.gradle.dsl.GraalVMExtension;
import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Compiles {@code logback.xml} into a Logback {@code Configurator} at build time, with Micronaut AOT's Logback
 * precompiler, and packages it into the jar, the fat jar, the JVM Docker image and the native image. The class
 * paths of {@code run} and {@code test} are left alone.
 *
 * @since 5.1.0
 */
public class MicronautLogbackPlugin implements Plugin<Project> {

    /** The name of the task that precompiles {@code logback.xml}. */
    public static final String PRECOMPILE_TASK_NAME = "precompileLogback";

    private static final String GENERATED_PACKAGE = "io/micronaut/aot/logback/generated/";
    private static final String CONFIGURATOR = "io.micronaut.aot.logback.generated.LogbackConfigurator";
    private static final String CONFIGURATOR_SERVICE = "META-INF/services/ch.qos.logback.classic.spi.Configurator";
    private static final Set<String> PACKAGING_TASKS = Set.of("jar", "shadowJar", "runnerJar");
    private static final Set<String> OPTIMIZED_JARS = Set.of("optimizedJitJar", "optimizedNativeJar");
    private static final Set<String> OPTIMIZED_SERVICE_FILES =
        Set.of("mergeServiceFilesForOptimizedJitJar", "mergeServiceFilesForOptimizedNativeJar");

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(MicronautBasePlugin.class);
        MicronautExtension micronaut = project.getExtensions().getByType(MicronautExtension.class);
        LogbackExtension logback = micronaut.getExtensions().create("logback", LogbackExtension.class);
        logback.getPrecompile().convention(true);
        project.getPluginManager().withPlugin("java", unused -> configure(project, logback));
    }

    private static void configure(Project project, LogbackExtension logback) {
        SourceSet main = PluginsHelper.findSourceSets(project).getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        TaskProvider<JavaCompile> compileJava = project.getTasks().named(main.getCompileJavaTaskName(), JavaCompile.class);
        TaskProvider<PrecompileLogback> precompile = project.getTasks().register(PRECOMPILE_TASK_NAME, PrecompileLogback.class, task -> {
            task.setDescription("Compiles logback.xml into a Logback Configurator for the packaged application");
            task.getApplicationOutput().from(project.getTasks().named(main.getProcessResourcesTaskName()));
            task.getApplicationOutput().from(main.getOutput().getClassesDirs());
            task.getRuntimeClasspath().from(project.getConfigurations().getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME));
            task.getTargetRelease().convention(compileJava.map(MicronautLogbackPlugin::targetRelease));
            task.getPrecompile().convention(logback.getPrecompile());
            task.getOutputDirectory().convention(project.getLayout().getBuildDirectory().dir("generated/logback/classes"));
            task.getReportFile().convention(project.getLayout().getBuildDirectory().file("generated/logback/report.txt"));
        });
        Provider<Directory> generated = precompile.flatMap(PrecompileLogback::getOutputDirectory);
        project.getTasks().withType(Jar.class).configureEach(jar -> {
            if (PACKAGING_TASKS.contains(jar.getName())) {
                jar.from(generated);
            }
        });
        project.getPluginManager().withPlugin("org.graalvm.buildtools.native", unused ->
            project.getExtensions().getByType(GraalVMExtension.class).getBinaries().named("main", binary ->
                binary.getClasspath().from(generated)));
        project.getPluginManager().withPlugin("io.micronaut.aot", unused -> keepOutOfReplacedLogbackXml(project));
    }

    private static int targetRelease(JavaCompile compile) {
        Integer release = compile.getOptions().getRelease().getOrNull();
        if (release != null) {
            return release;
        }
        return Integer.parseInt(JavaVersion.toVersion(compile.getTargetCompatibility()).getMajorVersion());
    }

    /**
     * The optimized jars copy the main jar and merge its service files. When Micronaut AOT replaces
     * {@code logback.xml} with its own configurator, the precompiled one is kept out of them, so that only one is
     * registered.
     */
    private static void keepOutOfReplacedLogbackXml(Project project) {
        MicronautExtension micronaut = project.getExtensions().getByType(MicronautExtension.class);
        Provider<Boolean> replaced = micronaut.getExtensions().getByType(AOTExtension.class).getReplaceLogbackXml();
        project.getTasks().withType(Jar.class).configureEach(jar -> {
            if (OPTIMIZED_JARS.contains(jar.getName())) {
                jar.exclude(new GeneratedClasses(replaced));
            }
        });
        project.getTasks().configureEach(task -> {
            if (OPTIMIZED_SERVICE_FILES.contains(task.getName())) {
                task.getInputs().property("logbackPrecompilerExcluded", replaced);
                task.doLast(new RemoveConfiguratorService(replaced));
            }
        });
    }

    private record GeneratedClasses(Provider<Boolean> replaced) implements Spec<FileTreeElement> {
        @Override
        public boolean isSatisfiedBy(FileTreeElement element) {
            return Boolean.TRUE.equals(replaced.get()) && element.getRelativePath().getPathString().startsWith(GENERATED_PACKAGE);
        }
    }

    private record RemoveConfiguratorService(Provider<Boolean> replaced) implements Action<Task> {
        @Override
        public void execute(Task task) {
            if (!Boolean.TRUE.equals(replaced.get())) {
                return;
            }
            for (var file : task.getOutputs().getFiles()) {
                Path service = file.toPath().resolve(CONFIGURATOR_SERVICE);
                if (Files.isRegularFile(service)) {
                    removeConfigurator(service);
                }
            }
        }

        private static void removeConfigurator(Path service) {
            try {
                List<String> lines = Files.readAllLines(service, StandardCharsets.UTF_8).stream()
                    .filter(line -> !line.trim().equals(CONFIGURATOR))
                    .toList();
                if (lines.stream().allMatch(String::isBlank)) {
                    Files.delete(service);
                } else {
                    Files.write(service, lines, StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
