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

import io.micronaut.gradle.AttributeUtils;
import io.micronaut.gradle.MicronautExtension;
import io.micronaut.gradle.PluginsHelper;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.dsl.DependencyHandler;
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
 * {@code io.micronaut.dev.MicronautDevMain} with the manifest; and test mode, with the
 * {@code mnTestRuntime} configuration, the {@code mnTestManifest} task and the {@code mnTest} task.
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
     * The name of the task that runs the tests in test mode. Not {@code test -t}, which is Gradle's continuous build.
     */
    public static final String TEST_TASK_NAME = "mnTest";

    /**
     * The name of the task that writes the manifest of test mode.
     */
    public static final String TEST_MANIFEST_TASK_NAME = "mnTestManifest";

    /**
     * The configuration holding the launcher, the HTML report and the JUnit Platform launcher, on top of the test
     * runtime classpath.
     */
    public static final String TEST_RUNTIME_CONFIGURATION = "mnTestRuntime";

    /**
     * The launcher's main class.
     */
    public static final String LAUNCHER_MAIN_CLASS = "io.micronaut.dev.MicronautDevMain";

    private static final String LAUNCHER_ARTIFACT = "io.micronaut:micronaut-dev";
    private static final String LIVERELOAD_ARTIFACT = "io.micronaut:micronaut-dev-livereload";
    private static final String TEST_REPORT_ARTIFACT = "io.micronaut:micronaut-dev-test-report";
    private static final String JUNIT_PLATFORM_GROUP = "org.junit.platform";
    private static final String MANIFEST_DIRECTORY = "micronaut-dev";
    private static final String TEST_MANIFEST_DIRECTORY = MANIFEST_DIRECTORY + "/test";
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
            configureManifest(project, task, dev, main, developmentRuntimeClasspath, developmentRuntimeClasspath, MANIFEST_DIRECTORY, manifestDirectory);
            task.getReloadableRoots().addAll(project.provider(() -> paths(main.getOutput().getFiles())));
            addProjectOutputs(project, task, developmentRuntimeClasspath);
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

        TaskProvider<MicronautTestManifest> testManifest = configureTestMode(project, dev, compilers, sourceSets, manifestDirectory);
        MicronautDevKotlinSupport.configure(project, dev, manifest, testManifest, compilers);
        configureTrigger(project, dev, manifest, manifestDirectory, sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME));
    }

    /**
     * Test mode: the {@code mnTestRuntime} configuration, the test runtime classpath plus the launcher, the
     * HTML report and the JUnit Platform launcher; the {@code mnTestManifest} task; and the {@code mnTest} task
     * that runs the launcher with it.
     */
    private static TaskProvider<MicronautTestManifest> configureTestMode(Project project, MicronautDevExtension dev, Configuration compilers, SourceSetContainer sourceSets, Provider<File> triggerDirectory) {
        dev.getTest().getSelection().convention("affected");
        dev.getTest().getInitialRun().convention(true);
        dev.getTest().getReportPath().convention("/tests/");
        SourceSet main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        SourceSet test = sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME);
        ConfigurationContainer configurations = project.getConfigurations();
        Configuration testRuntimeClasspath = configurations.getByName(test.getRuntimeClasspathConfigurationName());
        Configuration applicationRuntimeClasspath = configurations.getByName(main.getRuntimeClasspathConfigurationName());
        Configuration testRuntime = configurations.create(TEST_RUNTIME_CONFIGURATION, conf -> {
            conf.setCanBeConsumed(false);
            conf.setCanBeResolved(true);
            conf.setDescription("The test runtime classpath plus the development mode launcher and the JUnit Platform launcher");
            conf.extendsFrom(testRuntimeClasspath, compilers);
            // the attributes of the test runtime classpath, so that the projects depended on resolve as they do for the tests
            // (copyAttributes sets those of its first argument from its second)
            AttributeUtils.copyAttributes(project.getProviders(), conf, testRuntimeClasspath);
            DependencyHandler dependencies = project.getDependencies();
            conf.getDependencies().add(dependencies.create(LAUNCHER_ARTIFACT));
            conf.getDependencies().add(dependencies.create(TEST_REPORT_ARTIFACT));
            conf.getDependencies().addAllLater(dev.getLiveReload().getEnabled().map(enabled ->
                Boolean.TRUE.equals(enabled) ? List.of(dependencies.create(LIVERELOAD_ARTIFACT)) : List.of()));
            // at the version of the engine API the tests' engines resolved to, which the launcher must match
            conf.getDependencies().addAllLater(project.provider(() -> junitPlatformLauncher(dependencies, testRuntimeClasspath)));
        });

        TaskProvider<MicronautTestManifest> manifest = project.getTasks().register(TEST_MANIFEST_TASK_NAME, MicronautTestManifest.class, task -> {
            task.setGroup("verification");
            task.setDescription("Writes the manifest test mode reads");
            configureManifest(project, task, dev, main, testRuntimeClasspath, applicationRuntimeClasspath, TEST_MANIFEST_DIRECTORY, triggerDirectory);
            // the tests' outputs ahead of the application's, as on the build's test runtime classpath
            task.getReloadableRoots().addAll(project.provider(() -> paths(test.getOutput().getFiles())));
            task.getReloadableRoots().addAll(project.provider(() -> paths(main.getOutput().getFiles())));
            addProjectOutputs(project, task, testRuntimeClasspath);
            task.getTestJavaSources().from(test.getJava().getSrcDirs());
            task.getTestResourceSources().from(test.getResources().getSrcDirs());
            // the configuration, not the source set's classpath, which holds the application's classes and would build them:
            // the launcher adds the application's class outputs to the tests' compile classpath itself
            task.getTestCompileClasspath().from(configurations.getByName(test.getCompileClasspathConfigurationName()));
            TaskProvider<JavaCompile> compileTestJava = project.getTasks().named(test.getCompileJavaTaskName(), JavaCompile.class);
            task.getTestProcessorPath().from(project.provider(() -> compileTestJava.get().getOptions().getAnnotationProcessorPath() != null ? compileTestJava.get().getOptions().getAnnotationProcessorPath() : project.files()));
            task.getTestJavaOutput().set(project.provider(() -> compileTestJava.get().getDestinationDirectory().get().getAsFile().getAbsolutePath()));
            task.getTestJavaGeneratedSources().set(project.provider(() -> {
                File generated = compileTestJava.get().getOptions().getGeneratedSourceOutputDirectory().getAsFile().getOrNull();
                return generated == null ? null : generated.getAbsolutePath();
            }));
            task.getTestJavaOptions().set(project.provider(() -> javacOptions(compileTestJava.get())));
            task.getTestRunner().set("junit-platform");
            task.getTestSelection().set(dev.getTest().getSelection());
            task.getTestInitialRun().set(dev.getTest().getInitialRun());
            task.getTestParameters().set(dev.getTest().getParameters());
            task.getTestHtmlReportPath().set(dev.getTest().getReportPath());
            task.getTestReports().set(project.getLayout().getBuildDirectory().dir("test-results/" + TEST_TASK_NAME).map(dir -> dir.getAsFile().getAbsolutePath()));
            task.getTestHtmlReport().set(project.getLayout().getBuildDirectory().dir("reports/tests/" + TEST_TASK_NAME).map(dir -> dir.getAsFile().getAbsolutePath()));
            for (String argumentFile : List.of("test-compile", "test-processors", "test-java-options", "test-kotlin-options", "test-groovy-options")) {
                task.getArgumentFiles().from(project.getLayout().getBuildDirectory().file(TEST_MANIFEST_DIRECTORY + "/" + argumentFile + ".argfile"));
            }
            configureTestGroovy(project, task, test);
        });

        project.getTasks().register(TEST_TASK_NAME, MicronautTestTask.class, task -> {
            task.setGroup("verification");
            task.setDescription("Runs the tests in test mode: sources are compiled and the tests a change affects run as they change");
            task.dependsOn(manifest, main.getClassesTaskName(), test.getClassesTaskName());
            // the reloadable tier holds the outputs of the projects depended on too: built before the launch
            task.dependsOn(projectOutputs(project, testRuntimeClasspath, LibraryElements.CLASSES), projectOutputs(project, testRuntimeClasspath, LibraryElements.RESOURCES));
            task.getMainClass().set(LAUNCHER_MAIN_CLASS);
            FileCollection launcherClasspath = externalArtifacts(testRuntime);
            task.setClasspath(launcherClasspath);
            task.getArgumentProviders().add(new ManifestArgument(manifest.flatMap(MicronautDevManifest::getManifestFile).map(file -> file.getAsFile().getAbsolutePath())));
            task.getJvmArgumentProviders().add(new AgentArgument(launcherClasspath));
            task.getOverrides().set(project.getProviders().systemPropertiesPrefixedBy(MicronautDevManifest.PREFIX));
            // the keys that ask for runs are read from the terminal: attached when the task runs, so that the
            // configuration cache stores no stream
            task.doFirst(t -> ((JavaExec) t).setStandardInput(System.in));
            task.getOutputs().upToDateWhen(t -> false);
            task.setWorkingDir(project.getProjectDir());
            // as the test task runs them: an assert in a test or the code it tests fails it
            task.setEnableAssertions(true);
        });
        return manifest;
    }

    /**
     * The JUnit Platform launcher at the version of {@code junit-platform-engine} on the test runtime classpath, which
     * every engine, Jupiter, Spock or another, depends on: a launcher of another version may not link against it. None
     * when there is no engine, and so no test the JUnit Platform could run.
     */
    private static List<Dependency> junitPlatformLauncher(DependencyHandler dependencies, Configuration testRuntimeClasspath) {
        for (org.gradle.api.artifacts.result.ResolvedComponentResult component : testRuntimeClasspath.getIncoming().getResolutionResult().getAllComponents()) {
            ModuleVersionIdentifier id = component.getModuleVersion();
            if (id != null && JUNIT_PLATFORM_GROUP.equals(id.getGroup()) && "junit-platform-engine".equals(id.getName())) {
                return List.of(dependencies.create(JUNIT_PLATFORM_GROUP + ":junit-platform-launcher:" + id.getVersion()));
            }
        }
        return List.of();
    }

    /**
     * Describes the application to a manifest, of development or test mode: the settings, the classpaths, the
     * sources, and the compilations of the main source set. The modules come from one classpath, the sources of the projects
     * depended on from another: those of the application alone, since a project only the tests depend on cannot compile with
     * the application's classpath.
     */
    private static void configureManifest(Project project, MicronautDevManifest task, MicronautDevExtension dev, SourceSet main, Configuration classpath, Configuration sources, String directory, Provider<File> triggerDirectory) {
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
        task.getRuntimeClasspath().from(externalArtifacts(classpath));
        task.getCompileClasspath().from(main.getCompileClasspath());
        task.getJavaSources().from(main.getJava().getSrcDirs());
        task.getResourceSources().from(main.getResources().getSrcDirs());
        // the sources of the projects depended on too: their outputs are reloadable, so an edit there compiles
        // into this project's output, which the generation reads first
        task.getJavaSources().from(dependencySourceDirectories(project, sources, "java"));
        task.getResourceSources().from(dependencySourceDirectories(project, sources, "resources"));
        // the compilation is described, not run: the providers read the compile task without depending on it,
        // and mnDev or mnTest builds the classes before it runs
        TaskProvider<JavaCompile> compileJava = project.getTasks().named(main.getCompileJavaTaskName(), JavaCompile.class);
        task.getProcessorPath().from(project.provider(() -> compileJava.get().getOptions().getAnnotationProcessorPath() != null ? compileJava.get().getOptions().getAnnotationProcessorPath() : project.files()));
        task.getJavaOutput().set(project.provider(() -> compileJava.get().getDestinationDirectory().get().getAsFile().getAbsolutePath()));
        task.getJavaGeneratedSources().set(project.provider(() -> {
            File generated = compileJava.get().getOptions().getGeneratedSourceOutputDirectory().getAsFile().getOrNull();
            return generated == null ? null : generated.getAbsolutePath();
        }));
        task.getJavaOptions().set(project.provider(() -> javacOptions(compileJava.get())));
        task.getBuildToolTrigger().set(triggerDirectory.map(dir -> new File(dir, TRIGGER_FILE_NAME).getAbsolutePath()));
        task.getManifestFile().set(project.getLayout().getBuildDirectory().file(directory + "/" + MicronautDevManifest.MANIFEST_FILE_NAME));
        // one provider per file of the build layout: the configuration cache stores these, not a collection a transform built
        for (String argumentFile : List.of("runtime", "compile", "processors", "java-options", "kotlin-options", "groovy-options")) {
            task.getArgumentFiles().from(project.getLayout().getBuildDirectory().file(directory + "/" + argumentFile + ".argfile"));
        }
        configureGroovy(project, task, main, sources);
    }

    /**
     * The reloadable roots after the project's own: the outputs of the projects depended on. Named, not built: the
     * manifest describes the outputs, and the task that launches builds them before it runs.
     */
    private static void addProjectOutputs(Project project, MicronautDevManifest task, Configuration classpath) {
        task.getReloadableRoots().addAll(project.provider(() -> paths(projectOutputs(project, classpath, LibraryElements.CLASSES).getFiles())));
        task.getReloadableRoots().addAll(project.provider(() -> paths(projectOutputs(project, classpath, LibraryElements.RESOURCES).getFiles())));
    }

    /**
     * In build-tool mode the launcher compiles nothing and watches a trigger file: it is touched whenever
     * the classes were built, so that {@code ./gradlew classes -t} beside {@code mnDev} reloads the application,
     * and whenever the test classes were, so that {@code ./gradlew testClasses -t} beside {@code mnTest} runs the tests.
     */
    private static void configureTrigger(Project project, MicronautDevExtension dev, TaskProvider<MicronautDevManifest> manifest, Provider<File> manifestDirectory, SourceSet test) {
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
        project.getTasks().named(test.getClassesTaskName()).configure(classes -> classes.finalizedBy(touch));
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

    /**
     * The main source directories of one kind, {@code java}, {@code resources}, or the name of a source
     * directory set a language plugin adds to the source set such as {@code groovy} or {@code kotlin}, of
     * every project of this build the configuration reaches, transitively. The projects are found in the
     * resolution result, whose project identifiers every supported Gradle version has.
     *
     * @param project the project
     * @param configuration the configuration whose graph is walked
     * @param kind the kind of source directory
     * @return the directories, which may not exist
     */
    static Provider<List<File>> dependencySourceDirectories(Project project, Configuration configuration, String kind) {
        // a callable provider, evaluated when the configuration cache is stored, keeps only the directories: a
        // transform of the resolution result would be stored with the project it reads
        return project.provider(() -> {
            org.gradle.api.artifacts.result.ResolvedComponentResult root = configuration.getIncoming().getResolutionResult().getRootComponent().get();
            List<File> directories = new ArrayList<>();
            java.util.Set<String> seen = new java.util.HashSet<>();
            java.util.ArrayDeque<org.gradle.api.artifacts.result.ResolvedComponentResult> queue = new java.util.ArrayDeque<>();
            queue.add(root);
            while (!queue.isEmpty()) {
                org.gradle.api.artifacts.result.ResolvedComponentResult component = queue.poll();
                for (org.gradle.api.artifacts.result.DependencyResult dependency : component.getDependencies()) {
                    if (!(dependency instanceof org.gradle.api.artifacts.result.ResolvedDependencyResult resolved)) {
                        continue;
                    }
                    org.gradle.api.artifacts.result.ResolvedComponentResult selected = resolved.getSelected();
                    if (selected.getId() instanceof ProjectComponentIdentifier id && seen.add(id.getProjectPath())) {
                        Project dependencyProject = project.findProject(id.getProjectPath());
                        // a project of an included build shares no path with this build's: the name tells them apart
                        if (dependencyProject != null && dependencyProject != project && dependencyProject.getName().equals(id.getProjectName())) {
                            directories.addAll(mainSourceDirectories(dependencyProject, kind));
                        }
                        queue.add(selected);
                    }
                }
            }
            return directories;
        });
    }

    private static java.util.Set<File> mainSourceDirectories(Project project, String kind) {
        SourceSetContainer sourceSets = project.getExtensions().findByType(SourceSetContainer.class);
        SourceSet main = sourceSets == null ? null : sourceSets.findByName(SourceSet.MAIN_SOURCE_SET_NAME);
        if (main == null) {
            return java.util.Set.of();
        }
        return switch (kind) {
            case "java" -> main.getJava().getSrcDirs();
            case "resources" -> main.getResources().getSrcDirs();
            default -> main.getExtensions().findByName(kind) instanceof org.gradle.api.file.SourceDirectorySet directories
                ? directories.getSrcDirs()
                : java.util.Set.of();
        };
    }

    private static FileCollection projectOutputs(Project project, Configuration configuration, String libraryElements) {
        return configuration.getIncoming().artifactView(view -> {
            view.componentFilter(id -> id instanceof ProjectComponentIdentifier);
            view.attributes(attributes -> attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.getObjects().named(LibraryElements.class, libraryElements)));
            view.lenient(true);
        }).getFiles();
    }

    private static void configureGroovy(Project project, MicronautDevManifest task, SourceSet main, Configuration developmentRuntimeClasspath) {
        project.getPluginManager().withPlugin("groovy", unused -> {
            org.gradle.api.file.SourceDirectorySet groovy = main.getExtensions().getByType(org.gradle.api.file.SourceDirectorySet.class);
            task.getGroovySources().from(groovy.getSrcDirs());
            task.getGroovySources().from(dependencySourceDirectories(project, developmentRuntimeClasspath, "groovy"));
            TaskProvider<GroovyCompile> compileGroovy = project.getTasks().named(main.getCompileTaskName("groovy"), GroovyCompile.class);
            task.getGroovyOutput().set(project.provider(() -> compileGroovy.get().getDestinationDirectory().get().getAsFile().getAbsolutePath()));
            task.getGroovyOptions().set(project.provider(() -> groovycOptions(compileGroovy.get())));
        });
    }

    /**
     * The Groovy test sources, such as Spock specifications, and their compilation.
     */
    private static void configureTestGroovy(Project project, MicronautTestManifest task, SourceSet test) {
        project.getPluginManager().withPlugin("groovy", unused -> {
            org.gradle.api.file.SourceDirectorySet groovy = test.getExtensions().getByType(org.gradle.api.file.SourceDirectorySet.class);
            task.getTestGroovySources().from(groovy.getSrcDirs());
            TaskProvider<GroovyCompile> compileGroovy = project.getTasks().named(test.getCompileTaskName("groovy"), GroovyCompile.class);
            task.getTestGroovyOutput().set(project.provider(() -> compileGroovy.get().getDestinationDirectory().get().getAsFile().getAbsolutePath()));
            task.getTestGroovyOptions().set(project.provider(() -> groovycOptions(compileGroovy.get())));
        });
    }

    private static List<String> groovycOptions(GroovyCompile compile) {
        List<String> options = new ArrayList<>();
        if (compile.getGroovyOptions().getEncoding() != null) {
            options.add("--encoding=" + compile.getGroovyOptions().getEncoding());
        }
        if (!compile.getGroovyOptions().isParameters()) {
            options.add("--no-parameters");
        }
        return options;
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
        private static final String LAUNCHER_JAR_PREFIX = "micronaut-dev-";
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
                // micronaut-dev-<version>.jar, not one of its modules such as micronaut-dev-livereload or micronaut-dev-test-report
                if (name.startsWith(LAUNCHER_JAR_PREFIX) && name.endsWith(".jar") && name.length() > LAUNCHER_JAR_PREFIX.length() && Character.isDigit(name.charAt(LAUNCHER_JAR_PREFIX.length()))) {
                    return List.of("-javaagent:" + file.getAbsolutePath());
                }
            }
            return List.of();
        }
    }
}
