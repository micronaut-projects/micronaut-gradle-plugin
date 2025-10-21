/*
 * Copyright 2003-2021 the original author or authors.
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
package io.micronaut.gradle.testresources;

import io.micronaut.testresources.buildtools.ServerFactory;
import io.micronaut.testresources.buildtools.ServerUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.process.ExecOperations;
import org.gradle.work.Incremental;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * A task responsible for starting a test resources server.
 * The test resources server can be started for a single
 * build, for a continuous build, or outlive a single build.
 */
public abstract class StartTestResourcesService extends DefaultTask {

    /**
     * The classpath of the test resources server. Once
     * the test server is started, the classpath cannot
     * be updated.
     *
     * @return the server classpath
     */
    @InputFiles
    @Classpath
    @Incremental
    public abstract ConfigurableFileCollection getClasspath();

    /**
     * The directory where the settings to connect to
     * the test resources server will be written. This
     * directory is used by clients to determine how
     * to contact the server.
     *
     * @return the directory where to write settings
     */
    @OutputDirectory
    public abstract DirectoryProperty getSettingsDirectory();

    /**
     * This file is used by the test server once started,
     * to write the port where to contact it. It is used
     * internally before we can write the settings.
     *
     * @return the port file
     */
    @Internal
    public abstract RegularFileProperty getPortFile();

    /**
     * An explicit port to use when starting the test
     * resources server.
     *
     * @return an explicit port
     */
    @Input
    @Optional
    public abstract Property<Integer> getExplicitPort();

    /**
     * An access token which must be used by clients
     * when talking to the test resources server.
     *
     * @return the access token
     */
    @Input
    @Optional
    public abstract Property<String> getAccessToken();

    /**
     * Client timeout, in seconds, to the server.
     * This may have to be tweaked if spawning
     * test resources take longer than the default,
     * which is 60s.
     *
     * @return the client timeout
     */
    @Input
    @Optional
    public abstract Property<Integer> getClientTimeout();

    /**
     * Server idle timeout, in minutes. If the server
     * doesn't receive any request for this amount of
     * time, it will stop itself.
     *
     * @return the server idle timeout
     */
    @Input
    @Optional
    public abstract Property<Integer> getServerIdleTimeoutMinutes();

    /**
     * Allows starting the test server in foreground
     * instead of background, in which case the build
     * will block.
     *
     * @return the foreground property
     */
    @Internal
    @Option(option = "block", description = "Runs the test server in foreground, blocking until the server is stopped.")
    public abstract Property<Boolean> getForeground();

    /**
     * An internal file used to determine if the server
     * should be stopped at the end of a build or not.
     *
     * @return the stop file location
     */
    @Internal
    public abstract RegularFileProperty getStopFile();

    /**
     * If set to true, the service will be started with debug enabled.
     *
     * @return the debug flag
     */
    @Internal
    public abstract Property<Boolean> getDebugServer();

    /**
     * An internal property used to determine if the
     * server is in standalone mode, in which case it
     * will outlive the build (it will stay in the
     * background even if a build or a build session
     * ends)
     *
     * @return the standalone mode property
     */
    @Internal
    public abstract Property<Boolean> getStandalone();

    @Internal
    public abstract Property<Boolean> getUseClassDataSharing();

    @Internal
    public abstract DirectoryProperty getClassDataSharingDir();

    @Input
    public abstract MapProperty<String, String> getSystemProperties();

    @Input
    public abstract MapProperty<String, String> getEnvironment();

    @Nested
    @Optional
    public abstract Property<JavaLauncher> getJavaLauncher();

    @Optional
    @Input
    public abstract Property<String> getJavaExecutable();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Inject
    protected abstract ObjectFactory getObjects();

    @Inject
    public StartTestResourcesService() {
        setGroup(MicronautTestResourcesPlugin.GROUP);
        setDescription("Starts the test resources server");
    }

    @TaskAction
    public void startService() throws IOException {
        Path cdsDir = null;
        if (getUseClassDataSharing().get()) {
            cdsDir = getClassDataSharingDir().get().getAsFile().toPath();
        }
        ServerFactory serverFactory = new ServerFactory() {
            @Override
            public void startServer(ServerUtils.ProcessParameters processParameters) throws IOException {
                Path stopFilePath = getStopFile().getAsFile().get().toPath();
                if (!Files.exists(stopFilePath)) {
                    if (Boolean.TRUE.equals(getStandalone().get())) {
                        getLogger().lifecycle("Test resources server started in standalone mode. You can stop it by running the " + MicronautTestResourcesPlugin.STOP_TEST_RESOURCES_SERVICE + " task.");
                    }
                    String stop = getStandalone().map(v -> String.valueOf(!v)).get();
                    Files.createDirectories(stopFilePath.getParent());
                    Files.write(stopFilePath, Collections.singletonList(stop), StandardOpenOption.CREATE);
                }
                var javaLauncher = getJavaLauncher().getOrNull();
                if (Boolean.TRUE.equals(getForeground().get()) || processParameters.isCDSDumpInvocation()) {
                    startService(processParameters, javaLauncher);
                } else {
                    new Thread(() -> startService(processParameters, javaLauncher)).start();
                }
            }

            private void startService(ServerUtils.ProcessParameters processParameters, JavaLauncher javaLauncher) {
                var executable = getJavaExecutable().getOrNull();
                if (executable == null && javaLauncher != null) {
                    executable = javaLauncher.getExecutablePath().getAsFile().getAbsolutePath();
                }
                String finalExecutable = executable;
                var logger = getProject().getLogger();
                var result = getExecOperations().javaexec(spec -> {
                    if (finalExecutable != null) {
                        logger.info("Starting test resources service with Java at {}", finalExecutable);
                        spec.setExecutable(finalExecutable);
                    }
                    spec.getMainClass().set(processParameters.getMainClass());
                    spec.setDebug(getDebugServer().getOrElse(false));
                    List<File> classpath = processParameters.getClasspath();
                    spec.setClasspath(getObjects().fileCollection().from(classpath));
                    spec.setJvmArgs(processParameters.getJvmArguments());
                    processParameters.getSystemProperties().forEach(spec::systemProperty);
                    getSystemProperties().get().forEach(spec::systemProperty);
                    getEnvironment().get().forEach(spec::environment);
                    processParameters.getArguments().forEach(spec::args);
                });
                if (result.getExitValue() != 0) {
                    throw new GradleException("Test server failed to start");
                }
            }

            @Override
            public void waitFor(Duration duration) throws InterruptedException {
                Thread.sleep(duration.toMillis());
            }
        };
        ServerUtils.startOrConnectToExistingServer(
            getExplicitPort().getOrNull(),
            getPortFile().map(f -> f.getAsFile().toPath()).getOrNull(),
            getSettingsDirectory().get().getAsFile().toPath(),
            getAccessToken().getOrNull(),
            cdsDir,
            getClasspath().getFiles(),
            getClientTimeout().getOrNull(),
            getServerIdleTimeoutMinutes().getOrNull(),
            serverFactory);
    }

}
