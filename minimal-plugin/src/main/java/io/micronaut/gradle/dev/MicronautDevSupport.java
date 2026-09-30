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

import io.micronaut.gradle.MicronautExtension;
import io.micronaut.gradle.PluginsHelper;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.GroovyCompile;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.process.CommandLineArgumentProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Wires development mode into an application project: the {@code micronaut.dev} extension, the
 * {@code mnDevRuntime} configuration that adds the launcher to the development runtime classpath,
 * the {@code mnDevManifest} task, and the {@code mnDev} task that runs
 * {@code io.micronaut.dev.MicronautDevMain} with the manifest.
 *
 */
public final class MicronautDevSupport {

    /**
     * The name of the task that runs the application in development mode.
     */
    public static final String DEV_TASK_NAME = "mnDev";

    /**
     * The name of the task that writes the manifest.
     */
    public static final String MANIFEST_TASK_NAME = "mnDevManifest";

    /**
     * The configuration holding the launcher and what it needs, on top of the development runtime classpath.
     */
    public static final String DEV_RUNTIME_CONFIGURATION = "mnDevRuntime";

    /**
     * The configuration holding the compilers the embedded compilation needs: the Kotlin Build Tools
     * implementation and KSP at the project's versions.
     */
    public static final String DEV_COMPILER_CONFIGURATION = "mnDevCompiler";

    /**
     * The launcher's main class.
     */
    public static final String LAUNCHER_MAIN_CLASS = "io.micronaut.dev.MicronautDevMain";

    private static final String LAUNCHER_ARTIFACT = "io.micronaut:micronaut-dev";
    private static final String LIVERELOAD_ARTIFACT = "io.micronaut:micronaut-dev-livereload";
    private static final String MANIFEST_DIRECTORY = "micronaut-dev";
    private static final String TRIGGER_FILE_NAME = "reload";
    private static final String TRIGGER_TASK_NAME = "mnDevTrigger";

    private MicronautDevSupport() {
    }

    /**
     * Configures development mode for the project.
     *
     * @param project the project
     * @param developmentRuntimeClasspath the development runtime classpath the {@code run} task uses
     */
    public static void configure(Project project, Configuration developmentRuntimeClasspath) {
        MicronautExtension micronaut = project.getExtensions().getByType(MicronautExtension.class);
        MicronautDevExtension dev = micronaut.getExtensions().create("dev", MicronautDevExtension.class);
        dev.getStrategy().convention("auto");
        dev.getCompile().convention("embedded");
        dev.getIncremental().convention(true);
        dev.getLiveReload().getEnabled().convention(true);
        dev.getLiveReload().getPort().convention(35729);
        dev.getLiveReload().getInjectScript().convention(true);

        ConfigurationContainer configurations = project.getConfigurations();
        Configuration compilers = configurations.create(DEV_COMPILER_CONFIGURATION, conf -> {
            conf.setCanBeConsumed(false);
            conf.setCanBeResolved(true);
            conf.setDescription("The compilers the embedded compilation of development mode needs, at the project's versions");
        });
        Configuration devRuntime = configurations.create(DEV_RUNTIME_CONFIGURATION, conf -> {
            conf.setCanBeConsumed(false);
            conf.setCanBeResolved(true);
            conf.setDescription("The development runtime classpath plus the development mode launcher");
            conf.extendsFrom(developmentRuntimeClasspath, compilers);
            conf.getDependencies().add(project.getDependencies().create(LAUNCHER_ARTIFACT));
            conf.getDependencies().addAllLater(dev.getLiveReload().getEnabled().map(enabled ->
                Boolean.TRUE.equals(enabled) ? List.of(project.getDependencies().create(LIVERELOAD_ARTIFACT)) : List.of()));
        });

        SourceSetContainer sourceSets = PluginsHelper.findSourceSets(project);
        SourceSet main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        Provider<File> manifestDirectory = project.getLayout().getBuildDirectory().dir(MANIFEST_DIRECTORY).map(dir -> dir.getAsFile());

        TaskProvider<MicronautDevManifest> manifest = project.getTasks().register(MANIFEST_TASK_NAME, MicronautDevManifest.class, task -> {
            task.setGroup("application");
            task.setDescription("Writes the manifest development mode reads");
            JavaApplication application = project.getExtensions().getByType(JavaApplication.class);
            task.getMainClass().set(application.getMainClass());
            task.getProjectDirectory().set(project.getProjectDir().getAbsolutePath());
            task.getStrategy().set(dev.getStrategy());
            task.getCompileMode().set(dev.getCompile());
            task.getIncremental().set(dev.getIncremental());
            task.getRetain().set(dev.getRetain());
            task.getLiveReloadPort().set(dev.getLiveReload().getPort());
            task.getLiveReloadInjectScript().set(dev.getLiveReload().getInjectScript());
            // the modules of the parent loader, never the project's own outputs or those of the projects it depends on;
            // the launcher itself is on the JVM's classpath and needs no naming here
            task.getRuntimeClasspath().from(externalArtifacts(developmentRuntimeClasspath));
            // named, not built: the manifest describes the outputs, and mnDev builds them before it runs
            task.getReloadableRoots().addAll(project.provider(() -> paths(main.getOutput().getFiles())));
            task.getReloadableRoots().addAll(project.provider(() -> paths(projectOutputs(project, developmentRuntimeClasspath, LibraryElements.CLASSES).getFiles())));
            task.getReloadableRoots().addAll(project.provider(() -> paths(projectOutputs(project, developmentRuntimeClasspath, LibraryElements.RESOURCES).getFiles())));
            task.getCompileClasspath().from(main.getCompileClasspath());
            task.getJavaSources().from(main.getJava().getSrcDirs());
            task.getResourceSources().from(main.getResources().getSrcDirs());
            // the compilation is described, not run: the providers read the compile task without depending on it,
            // and mnDev builds the classes before it runs
            TaskProvider<JavaCompile> compileJava = project.getTasks().named(main.getCompileJavaTaskName(), JavaCompile.class);
            task.getProcessorPath().from(project.provider(() -> compileJava.get().getOptions().getAnnotationProcessorPath() != null ? compileJava.get().getOptions().getAnnotationProcessorPath() : project.files()));
            task.getJavaOutput().set(project.provider(() -> compileJava.get().getDestinationDirectory().get().getAsFile().getAbsolutePath()));
            task.getJavaGeneratedSources().set(project.provider(() -> {
                File generated = compileJava.get().getOptions().getGeneratedSourceOutputDirectory().getAsFile().getOrNull();
                return generated == null ? null : generated.getAbsolutePath();
            }));
            task.getJavaOptions().set(project.provider(() -> javacOptions(compileJava.get())));
            task.getBuildToolTrigger().set(manifestDirectory.map(dir -> new File(dir, TRIGGER_FILE_NAME).getAbsolutePath()));
            task.getManifestFile().set(project.getLayout().getBuildDirectory().file(MANIFEST_DIRECTORY + "/" + MicronautDevManifest.MANIFEST_FILE_NAME));
            task.getArgumentFiles().from(manifestDirectory.map(dir -> List.of(new File(dir, "runtime.argfile"), new File(dir, "compile.argfile"), new File(dir, "processors.argfile"),
                new File(dir, "java-options.argfile"), new File(dir, "kotlin-options.argfile"), new File(dir, "groovy-options.argfile"))));
            configureGroovy(project, task, main);
        });

        project.getTasks().register(DEV_TASK_NAME, JavaExec.class, task -> {
            task.setGroup("application");
            task.setDescription("Runs the application in development mode: sources are compiled and the application reloaded as they change");
            task.dependsOn(manifest, main.getClassesTaskName());
            // the reloadable tier holds the outputs of the projects depended on too: built before the launch
            task.dependsOn(projectOutputs(project, developmentRuntimeClasspath, LibraryElements.CLASSES), projectOutputs(project, developmentRuntimeClasspath, LibraryElements.RESOURCES));
            task.getMainClass().set(LAUNCHER_MAIN_CLASS);
            FileCollection launcherClasspath = externalArtifacts(devRuntime);
            task.setClasspath(launcherClasspath);
            task.getArgumentProviders().add(new ManifestArgument(manifest.flatMap(MicronautDevManifest::getManifestFile).map(file -> file.getAsFile().getAbsolutePath())));
            task.getJvmArgumentProviders().add(new AgentArgument(launcherClasspath));
            task.getOutputs().upToDateWhen(t -> false);
            task.setWorkingDir(project.getProjectDir());
            JavaApplication application = project.getExtensions().getByType(JavaApplication.class);
            task.jvmArgs(application.getApplicationDefaultJvmArgs());
        });

        MicronautDevKotlinSupport.configure(project, dev, manifest, compilers);
        configureTrigger(project, dev, manifest, manifestDirectory);
    }

    /**
     * In build-tool mode the launcher compiles nothing and watches a trigger file: it is touched whenever
     * the classes were built, so that {@code ./gradlew classes -t} beside {@code mnDev} reloads the application.
     */
    private static void configureTrigger(Project project, MicronautDevExtension dev, TaskProvider<MicronautDevManifest> manifest, Provider<File> manifestDirectory) {
        TaskProvider<org.gradle.api.Task> touch = project.getTasks().register(TRIGGER_TASK_NAME, task -> {
            task.setDescription("Touches the file development mode watches in build-tool mode");
            Provider<Boolean> buildTool = dev.getCompile().map("build-tool"::equals)
                .zip(manifest.flatMap(MicronautDevManifest::getKotlinCompileMode).orElse(""), (all, kotlin) -> all || "build-tool".equals(kotlin));
            task.onlyIf(t -> buildTool.get());
            Provider<File> trigger = manifestDirectory.map(dir -> new File(dir, TRIGGER_FILE_NAME));
            task.doLast(t -> {
                File file = trigger.get();
                try {
                    java.nio.file.Files.createDirectories(file.toPath().getParent());
                    java.nio.file.Files.writeString(file.toPath(), String.valueOf(System.currentTimeMillis()));
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException("Cannot touch " + file, e);
                }
            });
        });
        project.getTasks().named(org.gradle.api.plugins.JavaPlugin.CLASSES_TASK_NAME).configure(classes -> classes.finalizedBy(touch));
    }

    private static List<String> paths(java.util.Set<File> files) {
        List<String> paths = new ArrayList<>(files.size());
        for (File file : files) {
            paths.add(file.getAbsolutePath());
        }
        return paths;
    }

    /**
     * Everything of a configuration but the projects: the modules and the file dependencies, which the
     * parent loader holds, while the projects' outputs are the reloadable tier.
     */
    private static FileCollection externalArtifacts(Configuration configuration) {
        return configuration.getIncoming().artifactView(view -> view.componentFilter(id -> !(id instanceof ProjectComponentIdentifier))).getFiles();
    }

    private static FileCollection projectOutputs(Project project, Configuration configuration, String libraryElements) {
        return configuration.getIncoming().artifactView(view -> {
            view.componentFilter(id -> id instanceof ProjectComponentIdentifier);
            view.attributes(attributes -> attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.getObjects().named(LibraryElements.class, libraryElements)));
            view.lenient(true);
        }).getFiles();
    }

    private static void configureGroovy(Project project, MicronautDevManifest task, SourceSet main) {
        project.getPluginManager().withPlugin("groovy", unused -> {
            org.gradle.api.file.SourceDirectorySet groovy = main.getExtensions().getByType(org.gradle.api.file.SourceDirectorySet.class);
            task.getGroovySources().from(groovy.getSrcDirs());
            TaskProvider<GroovyCompile> compileGroovy = project.getTasks().named(main.getCompileTaskName("groovy"), GroovyCompile.class);
            task.getGroovyOutput().set(project.provider(() -> compileGroovy.get().getDestinationDirectory().get().getAsFile().getAbsolutePath()));
            task.getGroovyOptions().set(project.provider(() -> {
                GroovyCompile compile = compileGroovy.get();
                List<String> options = new ArrayList<>();
                if (compile.getGroovyOptions().getEncoding() != null) {
                    options.add("--encoding=" + compile.getGroovyOptions().getEncoding());
                }
                if (!compile.getGroovyOptions().isParameters()) {
                    options.add("--no-parameters");
                }
                return options;
            }));
        });
    }

    /**
     * The javac options as the build passes them: the compiler arguments, the release, and the encoding.
     */
    static List<String> javacOptions(JavaCompile compile) {
        List<String> options = new ArrayList<>(compile.getOptions().getAllCompilerArgs());
        if (compile.getOptions().getRelease().isPresent()) {
            options.add("--release");
            options.add(String.valueOf(compile.getOptions().getRelease().get()));
        } else {
            if (compile.getSourceCompatibility() != null) {
                options.add("-source");
                options.add(compile.getSourceCompatibility());
            }
            if (compile.getTargetCompatibility() != null) {
                options.add("-target");
                options.add(compile.getTargetCompatibility());
            }
        }
        if (compile.getOptions().getEncoding() != null) {
            options.add("-encoding");
            options.add(compile.getOptions().getEncoding());
        }
        return options;
    }

    /**
     * The {@code --manifest} argument.
     */
    static final class ManifestArgument implements CommandLineArgumentProvider {
        private final Provider<String> manifest;

        ManifestArgument(Provider<String> manifest) {
            this.manifest = manifest;
        }

        @org.gradle.api.tasks.Input
        Provider<String> getManifest() {
            return manifest;
        }

        @Override
        public Iterable<String> asArguments() {
            return List.of("--manifest", manifest.get());
        }
    }

    /**
     * The launcher as the JVM's agent, for the method-body fast path: {@code -javaagent:<micronaut-dev jar>}.
     */
    static final class AgentArgument implements CommandLineArgumentProvider {
        private final FileCollection classpath;

        AgentArgument(FileCollection classpath) {
            this.classpath = classpath;
        }

        @org.gradle.api.tasks.Classpath
        FileCollection getClasspath() {
            return classpath;
        }

        @Override
        public Iterable<String> asArguments() {
            for (File file : classpath.getFiles()) {
                String name = file.getName();
                if (name.startsWith("micronaut-dev-") && name.endsWith(".jar") && !name.startsWith("micronaut-dev-livereload") && !name.startsWith("micronaut-dev-tck")) {
                    return List.of("-javaagent:" + file.getAbsolutePath());
                }
            }
            return List.of();
        }
    }
}
