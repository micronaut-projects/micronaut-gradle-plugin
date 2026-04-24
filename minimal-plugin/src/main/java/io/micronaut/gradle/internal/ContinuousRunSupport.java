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
package io.micronaut.gradle.internal;

import org.gradle.api.GradleException;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.JavaExec;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.jvm.toolchain.JavaLauncher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Supports non-blocking continuous {@code run} execution so Gradle can schedule
 * multiple Micronaut application projects in parallel.
 */
public final class ContinuousRunSupport {
    private static final long PROCESS_STOP_TIMEOUT_SECONDS = 5;
    private static final String INTERNAL_STARTUP_TIMEOUT_MILLIS_PROPERTY = "io.micronaut.internal.gradle.continuous.startup.timeout";
    private static final long DEFAULT_STARTUP_TIMEOUT_MILLIS = 5_000;
    private static final long STARTUP_POLL_INTERVAL_MILLIS = 100;
    private static final Map<Path, ProcessHandle> RUNNING_PROCESSES = new ConcurrentHashMap<>();
    private static volatile boolean shutdownHookInstalled;

    private ContinuousRunSupport() {
    }

    public static void launch(JavaExec javaExec, java.io.File stateFile) {
        Path statePath = stateFile.toPath().toAbsolutePath().normalize();
        Logger logger = javaExec.getLogger();

        stopPreviousProcess(statePath, logger);

        List<String> commandLine = createCommandLine(javaExec);

        try {
            Files.createDirectories(statePath.getParent());
            ProcessBuilder builder = new ProcessBuilder(commandLine);
            if (javaExec.getWorkingDir() != null) {
                builder.directory(javaExec.getWorkingDir());
            }
            builder.inheritIO();
            builder.environment().putAll(stringifyEnvironment(javaExec.getEnvironment()));

            Process process = builder.start();
            ProcessHandle handle = process.toHandle();
            boolean cancelBuildOnFailure = javaExec.getProject().getGradle().getStartParameter().isContinuous();
            BuildCancellationToken cancellationToken = ((ProjectInternal) javaExec.getProject()).getServices().get(BuildCancellationToken.class);

            writeState(statePath, commandLine.get(0), handle);
            registerShutdownCleanup(statePath, handle);

            if (!waitForFailedStartup(process, logger, statePath, cancellationToken, cancelBuildOnFailure)) {
                logger.lifecycle("Started background continuous run process for {} (pid: {}).", javaExec.getPath(), handle.pid());
            }
        } catch (IOException e) {
            throw new GradleException("Unable to launch Micronaut run task in the background for continuous mode.", e);
        }
    }

    private static Map<String, String> stringifyEnvironment(Map<String, Object> environment) {
        return environment.entrySet().stream()
            .filter(entry -> entry.getKey() != null && entry.getValue() != null)
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                entry -> String.valueOf(entry.getValue())
            ));
    }

    private static String resolveExecutable(JavaExec javaExec, String commandLineExecutable) {
        if (commandLineExecutable != null) {
            return commandLineExecutable;
        }
        String executable = javaExec.getExecutable();
        if (executable != null) {
            return executable;
        }
        JavaLauncher launcher = javaExec.getJavaLauncher().getOrNull();
        if (launcher != null) {
            return launcher.getExecutablePath().getAsFile().getAbsolutePath();
        }
        throw new GradleException("Cannot resolve a Java executable for the continuous run launcher.");
    }

    private static List<String> createCommandLine(JavaExec javaExec) {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(resolveExecutable(javaExec, javaExec.getExecutable()));
        commandLine.addAll(javaExec.getAllJvmArgs());
        if (!javaExec.getClasspath().isEmpty()) {
            commandLine.add("-cp");
            commandLine.add(javaExec.getClasspath().getAsPath());
        }
        String mainModule = javaExec.getMainModule().getOrNull();
        String mainClass = javaExec.getMainClass().getOrNull();
        if (mainModule != null) {
            commandLine.add("-m");
            commandLine.add(mainClass == null ? mainModule : mainModule + "/" + mainClass);
        } else if (mainClass != null) {
            commandLine.add(mainClass);
        } else {
            throw new GradleException("Cannot launch a continuous run process without a configured main class or module.");
        }
        commandLine.addAll(javaExec.getArgs());
        javaExec.getArgumentProviders().forEach(provider -> provider.asArguments().forEach(commandLine::add));
        return commandLine;
    }

    private static void stopPreviousProcess(Path statePath, Logger logger) {
        ProcessHandle previous = RUNNING_PROCESSES.remove(statePath);
        if (previous != null && previous.isAlive()) {
            stopProcess(previous, logger, "Stopping previous in-memory continuous run process.");
        }

        if (!Files.exists(statePath)) {
            return;
        }
        Properties properties = readState(statePath);
        String pidValue = properties.getProperty("pid");
        if (pidValue == null) {
            deleteState(statePath, logger);
            return;
        }
        long pid;
        try {
            pid = Long.parseLong(pidValue);
        } catch (NumberFormatException e) {
            deleteState(statePath, logger);
            return;
        }
        ProcessHandle.of(pid).ifPresent(handle -> {
            if (matchesRecordedProcess(handle, properties)) {
                stopProcess(handle, logger, "Stopping previous continuous run process.");
            } else {
                logger.warn("Skipping shutdown for stale continuous run PID {} because ownership could not be verified.", pid);
            }
        });
        deleteState(statePath, logger);
    }

    private static boolean matchesRecordedProcess(ProcessHandle handle, Properties properties) {
        String expectedCommand = properties.getProperty("command");
        String expectedStartedAt = properties.getProperty("startedAt");
        ProcessHandle.Info info = handle.info();
        if (expectedCommand == null || expectedStartedAt == null) {
            return false;
        }
        boolean commandMatches = info.command()
            .map(Path::of)
            .map(Path::toAbsolutePath)
            .map(Path::normalize)
            .map(Path::toString)
            .filter(expectedCommand::equals)
            .isPresent();
        boolean startedAtMatches = info.startInstant()
            .map(Instant::toEpochMilli)
            .map(String::valueOf)
            .filter(expectedStartedAt::equals)
            .isPresent();
        return commandMatches && startedAtMatches;
    }

    private static void stopProcess(ProcessHandle handle, Logger logger, String message) {
        logger.info("{} PID={}", message, handle.pid());
        handle.destroy();
        try {
            handle.onExit().get(PROCESS_STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            if (handle.isAlive()) {
                handle.destroyForcibly();
                try {
                    handle.onExit().get(PROCESS_STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                    logger.warn("Failed to wait for continuous run process {} to stop cleanly.", handle.pid());
                }
            }
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void writeState(Path statePath, String command, ProcessHandle handle) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("pid", String.valueOf(handle.pid()));
        properties.setProperty("command", new java.io.File(command).getAbsoluteFile().toPath().normalize().toString());
        handle.info().startInstant().map(Instant::toEpochMilli).map(String::valueOf).ifPresent(startedAt -> properties.setProperty("startedAt", startedAt));
        try (OutputStream out = Files.newOutputStream(statePath)) {
            properties.store(out, "Micronaut continuous run state");
        }
    }

    private static Properties readState(Path statePath) {
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(statePath)) {
            properties.load(in);
        } catch (IOException ignored) {
            return properties;
        }
        return properties;
    }

    private static boolean waitForFailedStartup(Process process,
                                                Logger logger,
                                                Path statePath,
                                                BuildCancellationToken cancellationToken,
                                                boolean cancelBuildOnFailure) {
        try {
            long timeoutMillis = startupTimeoutMillis();
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            while (true) {
                if (!process.isAlive()) {
                    int exitValue = process.exitValue();
                    if (exitValue != 0) {
                        logger.error("Continuous run process exited during startup with code {}.", exitValue);
                        deleteState(statePath, logger);
                        requestBuildCancellation(cancellationToken, cancelBuildOnFailure);
                        throw new GradleException("Continuous run process exited during startup with code " + exitValue + ".");
                    }
                    return true;
                }
                long remainingNanos = deadline - System.nanoTime();
                if (remainingNanos <= 0) {
                    return false;
                }
                long remainingMillis = Math.max(1, TimeUnit.NANOSECONDS.toMillis(remainingNanos));
                process.waitFor(Math.min(remainingMillis, STARTUP_POLL_INTERVAL_MILLIS), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            deleteState(statePath, logger);
            requestBuildCancellation(cancellationToken, cancelBuildOnFailure);
            throw new GradleException("Interrupted while launching the continuous run process.", e);
        }
    }

    private static long startupTimeoutMillis() {
        return Math.max(0, Long.getLong(INTERNAL_STARTUP_TIMEOUT_MILLIS_PROPERTY, DEFAULT_STARTUP_TIMEOUT_MILLIS));
    }

    private static void requestBuildCancellation(BuildCancellationToken cancellationToken, boolean cancelBuildOnFailure) {
        if (cancelBuildOnFailure) {
            cancellationToken.cancel();
        }
    }

    private static synchronized void registerShutdownCleanup(Path statePath, ProcessHandle handle) {
        RUNNING_PROCESSES.put(statePath, handle);
        handle.onExit().thenRun(() -> {
            RUNNING_PROCESSES.remove(statePath, handle);
            try {
                Files.deleteIfExists(statePath);
            } catch (IOException ignored) {
            }
        });
        if (!shutdownHookInstalled) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> RUNNING_PROCESSES.forEach((path, processHandle) -> {
                if (processHandle.isAlive()) {
                    processHandle.destroyForcibly();
                }
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            }), "micronaut-continuous-run-shutdown"));
            shutdownHookInstalled = true;
        }
    }

    private static void deleteState(Path statePath, Logger logger) {
        try {
            Files.deleteIfExists(statePath);
        } catch (IOException e) {
            logger.warn("Failed to delete continuous run state file {}.", statePath, e);
        }
    }
}
