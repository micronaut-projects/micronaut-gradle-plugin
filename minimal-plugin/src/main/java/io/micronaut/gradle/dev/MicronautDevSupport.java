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
import io.micronaut.gradle.internal.MicronautDevOutputLocks;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.provider.Property;
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
     * The configuration holding the Micronaut Control Panel modules of development mode, which the plugin infers
     * from the modules the application uses.
     */
    public static final String DEV_CONTROL_PANEL_CONFIGURATION = "mnDevControlPanel";

    /**
     * The modules the application sees in development mode: the development runtime classpath plus Micronaut
     * Control Panel, resolved together so that the platform aligns their versions.
     */
    public static final String DEV_APPLICATION_CLASSPATH_CONFIGURATION = "mnDevApplicationClasspath";

    /**
     * The name of the task that writes the defaults of Micronaut Control Panel in development mode.
     */
    public static final String CONTROL_PANEL_DEFAULTS_TASK_NAME = "mnDevControlPanelDefaults";

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

    /**
     * The LiveReload port of {@code mnDev} by default.
     */
    static final int DEFAULT_LIVERELOAD_PORT = 35729;

    /**
     * The LiveReload port of {@code mnTest} by default, the next one, so that its live report is served while
     * {@code mnDev} runs.
     */
    static final int DEFAULT_TEST_LIVERELOAD_PORT = 35730;

    private static final String LAUNCHER_ARTIFACT = "io.micronaut:micronaut-dev";
    private static final String LIVERELOAD_ARTIFACT = "io.micronaut:micronaut-dev-livereload";
    private static final String TEST_REPORT_ARTIFACT = "io.micronaut:micronaut-dev-test-report";
    private static final String JUNIT_PLATFORM_GROUP = "org.junit.platform";
    /**
     * The usage of the test resources server's settings, which {@code io.micronaut.test-resources} publishes and
     * {@code io.micronaut.test-resources-consumer} resolves on the development and test runtime classpaths.
     */
    private static final String TEST_RESOURCES_USAGE = "micronaut.test.resources";
    private static final String MANIFEST_DIRECTORY = "micronaut-dev";
    private static final String TEST_MANIFEST_DIRECTORY = MANIFEST_DIRECTORY + "/test";
    private static final String TRIGGER_FILE_NAME = "reload";
    private static final String TRIGGER_TASK_NAME = "mnDevTrigger";
    private static final String GENERATIONS_DIRECTORY = "generations";
    private static final String LOCK_TASK_NAME = "mnDevLockOutputs";
    private static final String UNLOCK_TASK_NAME = "mnDevUnlockOutputs";

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
        dev.getLiveReload().getPort().convention(DEFAULT_LIVERELOAD_PORT);
        dev.getLiveReload().getInjectScript().convention(true);
        dev.getBindLoopback().convention(true);
        dev.getControlPanel().getEnabled().convention(true);
        Provider<Boolean> controlPanelEnabled = MicronautDevControlPanel.enabled(project, dev);

        ConfigurationContainer configurations = project.getConfigurations();
        Configuration controlPanel = configurations.create(DEV_CONTROL_PANEL_CONFIGURATION, conf -> {
            conf.setCanBeConsumed(false);
            conf.setCanBeResolved(false);
            conf.setDescription("The Micronaut Control Panel modules of development mode, inferred from the modules the application uses");
            DependencyHandler dependencies = project.getDependencies();
            // the panels of the modules the development runtime classpath resolves, at the versions of the platform it holds
            conf.getDependencies().addAllLater(controlPanelEnabled.map(enabled ->
                Boolean.TRUE.equals(enabled) ? MicronautDevControlPanel.dependencies(dependencies, developmentRuntimeClasspath) : List.of()));
        });
        // the application's modules and Control Panel resolved together, apart from the runtime classpath of production
        Configuration applicationClasspath = configurations.create(DEV_APPLICATION_CLASSPATH_CONFIGURATION, conf -> {
            conf.setCanBeConsumed(false);
            conf.setCanBeResolved(true);
            conf.setDescription("The modules the application sees in development mode: the development runtime classpath plus Micronaut Control Panel");
            conf.extendsFrom(developmentRuntimeClasspath, controlPanel);
            AttributeUtils.copyAttributes(project.getProviders(), conf, developmentRuntimeClasspath);
        });
        TaskProvider<MicronautDevControlPanelDefaults> controlPanelDefaults = project.getTasks().register(CONTROL_PANEL_DEFAULTS_TASK_NAME, MicronautDevControlPanelDefaults.class, task -> {
            task.setDescription("Writes the defaults of Micronaut Control Panel in development mode");
            // none while Control Panel is off: an application that adds Control Panel itself keeps its own defaults
            // with Micronaut Security on the classpath, the application's security decides who opens the panels
            task.getProperties().putAll(controlPanelEnabled.map(enabled -> Boolean.TRUE.equals(enabled)
                ? MicronautDevControlPanel.defaults(MicronautDevControlPanel.moduleKeys(developmentRuntimeClasspath))
                : java.util.Map.of()));
            task.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir(MANIFEST_DIRECTORY + "/control-panel"));
        });
        FileCollection controlPanelResources = project.files(controlPanelDefaults.flatMap(MicronautDevControlPanelDefaults::getOutputDirectory));
        Configuration compilers = configurations.create(DEV_COMPILER_CONFIGURATION, conf -> {
            conf.setCanBeConsumed(false);
            conf.setCanBeResolved(true);
            conf.setDescription("The compilers the embedded compilation of development mode needs, at the project's versions");
        });
        Configuration devRuntime = configurations.create(DEV_RUNTIME_CONFIGURATION, conf -> {
            conf.setCanBeConsumed(false);
            conf.setCanBeResolved(true);
            conf.setDescription("The development runtime classpath plus the development mode launcher");
            conf.extendsFrom(applicationClasspath, compilers);
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
            configureManifest(project, task, dev, main, lenientExternalArtifacts(applicationClasspath), developmentRuntimeClasspath, MANIFEST_DIRECTORY, manifestDirectory);
            task.dependsOn(controlPanelDefaults);
            task.getRuntimeClasspath().from(controlPanelResources);
            task.doFirst(new MicronautDevControlPanel.VerifyResolution(MicronautDevControlPanel.unresolved(project, applicationClasspath)));
            // the launcher binds the server to localhost unless this is off or the application sets a host
            task.getBindLoopback().set(bindLoopback(project, dev));
            task.getReloadableRoots().addAll(project.provider(() -> paths(main.getOutput().getFiles())));
            addProjectOutputs(project, task, developmentRuntimeClasspath);
        });

        project.getTasks().register(DEV_TASK_NAME, JavaExec.class, task -> {
            task.setGroup("application");
            task.setDescription("Runs the application in development mode: sources are compiled and the application reloaded as they change");
            task.dependsOn(manifest, main.getClassesTaskName(), controlPanelDefaults);
            // the reloadable tier holds the outputs of the projects depended on too: built before the launch
            task.dependsOn(projectOutputs(project, developmentRuntimeClasspath, LibraryElements.CLASSES), projectOutputs(project, developmentRuntimeClasspath, LibraryElements.RESOURCES));
            task.getMainClass().set(LAUNCHER_MAIN_CLASS);
            // lenient for the Control Panel modules alone, which development mode does without when they are not published
            FileCollection launcherClasspath = lenientExternalArtifacts(devRuntime);
            task.doFirst(new MicronautDevControlPanel.VerifyResolution(MicronautDevControlPanel.unresolved(project, devRuntime)));
            task.setClasspath(launcherClasspath.plus(controlPanelResources));
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
        configureOutputLocks(project, main, sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME));
    }

    /**
     * Holds the locks of the class outputs, those {@code micronaut-dev} takes, while the compile tasks of the main and
     * test source sets, and {@code clean}, run: {@code mnDevLockOutputs} takes them before, {@code mnDevUnlockOutputs} releases them after,
     * as their finalizer, and the launch of {@code mnDev} and {@code mnTest} releases them before its JVM takes them.
     * Development mode beside test mode, or beside any build of the project, then never compiles into an output Gradle
     * is checking or writing.
     */
    private static void configureOutputLocks(Project project, SourceSet main, SourceSet test) {
        Provider<MicronautDevOutputLocks> locks = project.getGradle().getSharedServices().registerIfAbsent(MicronautDevOutputLocks.SERVICE_NAME, MicronautDevOutputLocks.class, spec -> { });
        // named, not built: a callable provider the configuration cache stores as its value
        Provider<List<String>> classOutputs = project.provider(() -> {
            List<String> outputs = new ArrayList<>(paths(main.getOutput().getClassesDirs().getFiles()));
            outputs.addAll(paths(test.getOutput().getClassesDirs().getFiles()));
            return outputs;
        });
        TaskProvider<MicronautDevOutputLocks.LockTask> lock = project.getTasks().register(LOCK_TASK_NAME, MicronautDevOutputLocks.LockTask.class, task -> {
            task.setDescription("Takes the locks development and test mode hold while they compile the class outputs, waiting for them");
            task.getLocks().set(locks);
            task.usesService(locks);
            task.getClassOutputs().set(classOutputs);
            task.getRelease().set(false);
        });
        java.util.Set<String> compileTasks = new java.util.HashSet<>();
        for (SourceSet sourceSet : List.of(main, test)) {
            compileTasks.add(sourceSet.getCompileJavaTaskName());
            compileTasks.add(sourceSet.getCompileTaskName("groovy"));
            compileTasks.add(sourceSet.getCompileTaskName("kotlin"));
        }
        TaskProvider<MicronautDevOutputLocks.LockTask> unlock = project.getTasks().register(UNLOCK_TASK_NAME, MicronautDevOutputLocks.LockTask.class, task -> {
            task.setDescription("Releases the locks of the class outputs once they are compiled");
            task.getLocks().set(locks);
            task.usesService(locks);
            task.getClassOutputs().set(classOutputs);
            task.getRelease().set(true);
            // after every compile task, and clean, the build runs, whichever finishes last; found by name when the graph is built
            org.gradle.api.tasks.TaskContainer tasks = project.getTasks();
            task.mustRunAfter((java.util.concurrent.Callable<List<TaskProvider<org.gradle.api.Task>>>) () -> java.util.stream.Stream.concat(compileTasks.stream(), java.util.stream.Stream.of(BasePlugin.CLEAN_TASK_NAME))
                .filter(tasks.getNames()::contains)
                .map(tasks::named)
                .toList());
        });
        project.getTasks().configureEach(task -> {
            boolean clean = BasePlugin.CLEAN_TASK_NAME.equals(task.getName());
            if (compileTasks.contains(task.getName()) || clean) {
                // before the task, not in its actions: Gradle checks the outputs, and removes stale ones, before them
                task.dependsOn(lock);
                task.finalizedBy(unlock);
            }
            if (clean) {
                // clean deleted the files the build locked: the files at their paths are locked, before the old ones go
                task.usesService(locks);
                task.doLast(new MicronautDevOutputLocks.LockAction(locks, classOutputs, false));
            }
        });
        for (String launch : List.of(DEV_TASK_NAME, TEST_TASK_NAME)) {
            project.getTasks().named(launch, JavaExec.class, task -> {
                // the launcher's JVM takes the locks while the build waits for it: they must be free by then
                task.mustRunAfter(unlock);
                task.usesService(locks);
                task.doFirst(new MicronautDevOutputLocks.LockAction(locks, classOutputs, true));
            });
        }
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
        // not mnDev's port: with both running, the live report is served beside the application's LiveReload server
        dev.getTest().getLiveReloadPort().convention(DEFAULT_TEST_LIVERELOAD_PORT);
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
            configureManifest(project, task, dev, main, externalArtifacts(testRuntimeClasspath), applicationRuntimeClasspath, TEST_MANIFEST_DIRECTORY, triggerDirectory);
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
            task.getLiveReloadPort().set(dev.getTest().getLiveReloadPort());
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
     * sources, and the compilations of the main source set. The modules are given, the sources of the projects
     * depended on from another: those of the application alone, since a project only the tests depend on cannot compile with
     * the application's classpath.
     */
    private static void configureManifest(Project project, MicronautDevManifest task, MicronautDevExtension dev, SourceSet main, FileCollection modules, Configuration sources, String directory, Provider<File> triggerDirectory) {
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
        task.getRuntimeClasspath().from(modules);
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
        // apart for development and test mode, which may run at once
        task.getGenerations().set(project.getLayout().getBuildDirectory().dir(directory + "/" + GENERATIONS_DIRECTORY).map(dir -> dir.getAsFile().getAbsolutePath()));
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
        task.getReloadableRoots().addAll(project.provider(() -> projectCodeOutputs(project, classpath, LibraryElements.CLASSES)));
        task.getReloadableRoots().addAll(project.provider(() -> projectCodeOutputs(project, classpath, LibraryElements.RESOURCES)));
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
     * Whether the application listens on {@code localhost} by default in development mode: the Gradle property
     * {@code micronaut.dev.bind-loopback} if it is set, else the DSL.
     *
     * @param project the project
     * @param dev the extension
     * @return the flag
     */
    static Provider<Boolean> bindLoopback(Project project, MicronautDevExtension dev) {
        Provider<String> property = project.getProviders().gradleProperty(MicronautDevManifest.BIND_LOOPBACK);
        Property<Boolean> dsl = dev.getBindLoopback();
        // a callable provider, which the configuration cache stores as its value
        return project.provider(() -> {
            String value = property.getOrNull();
            return value != null ? Boolean.parseBoolean(value.trim()) : dsl.getOrElse(true);
        });
    }

    /**
     * As {@link #externalArtifacts(Configuration)}, without the modules that do not resolve: the task that uses them
     * runs {@link MicronautDevControlPanel.VerifyResolution} first, which fails for any but a Control Panel module.
     */
    private static FileCollection lenientExternalArtifacts(Configuration configuration) {
        return configuration.getIncoming().artifactView(view -> {
            view.componentFilter(id -> !(id instanceof ProjectComponentIdentifier));
            view.lenient(true);
        }).getFiles();
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
                    if (!isCodeVariant(resolved.getResolvedVariant().getAttributes())) {
                        // a project depended on for something else than its code, such as the provider of the test
                        // resources server, whose settings a consumer resolves
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

    /**
     * Whether a resolved variant holds a project's code, as any variant does but the settings of the test resources
     * server, which a consumer of the server resolves from the project that provides it.
     */
    private static boolean isCodeVariant(org.gradle.api.attributes.AttributeContainer attributes) {
        for (Attribute<?> attribute : attributes.keySet()) {
            if (Usage.USAGE_ATTRIBUTE.getName().equals(attribute.getName())) {
                return !TEST_RESOURCES_USAGE.equals(String.valueOf(attributes.getAttribute(attribute)));
            }
        }
        return true;
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
        return projectOutputView(project, configuration, libraryElements).getFiles();
    }

    /**
     * The outputs of the projects depended on that hold their code: not those of another variant, such as the
     * settings of the test resources server a consumer resolves from the project that provides it, which name no
     * library elements and so match any.
     */
    private static List<String> projectCodeOutputs(Project project, Configuration configuration, String libraryElements) {
        List<String> paths = new ArrayList<>();
        for (org.gradle.api.artifacts.result.ResolvedArtifactResult artifact : projectOutputView(project, configuration, libraryElements).getArtifacts().getArtifacts()) {
            if (isCodeVariant(artifact.getVariant().getAttributes())) {
                paths.add(artifact.getFile().getAbsolutePath());
            }
        }
        return paths;
    }

    private static org.gradle.api.artifacts.ArtifactView projectOutputView(Project project, Configuration configuration, String libraryElements) {
        return configuration.getIncoming().artifactView(view -> {
            view.componentFilter(id -> id instanceof ProjectComponentIdentifier);
            view.attributes(attributes -> attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.getObjects().named(LibraryElements.class, libraryElements)));
            view.lenient(true);
        });
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
