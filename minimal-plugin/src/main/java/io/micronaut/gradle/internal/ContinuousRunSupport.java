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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Supports non-blocking continuous {@code run} execution so Gradle can schedule
 * multiple Micronaut application projects in parallel.
 */
public final class ContinuousRunSupport {
    private static final long PROCESS_STOP_TIMEOUT_SECONDS = 5;
    private static final String OWNER_TOKEN_PROPERTY = "io.micronaut.internal.gradle.continuous.owner";
    private static final String OWNER_TOKEN_ARGUMENT_PREFIX = "-D" + OWNER_TOKEN_PROPERTY + "=";
    private static final String INTERNAL_STARTUP_TIMEOUT_MILLIS_PROPERTY = "io.micronaut.internal.gradle.continuous.startup.timeout";
    private static final long DEFAULT_STARTUP_TIMEOUT_MILLIS = 5_000;
    private static final long STARTUP_POLL_INTERVAL_MILLIS = 100;
    private static final Map<Path, ProcessHandle> RUNNING_PROCESSES = new ConcurrentHashMap<>();
    private static volatile boolean shutdownHookInstalled;

    private ContinuousRunSupport() {
    }

    public static void launch(JavaExec javaExec, java.io.File stateFile) {
        Path statePath = stateFile.toPath().toAbsolutePath().normalize();
        String ownerToken = ownerToken(statePath);
        Logger logger = javaExec.getLogger();

        stopPreviousProcess(statePath, ownerToken, logger);

        List<String> commandLine = createCommandLine(javaExec, ownerToken);

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

            writeState(statePath, commandLine.get(0), ownerToken, handle);
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

    private static String resolveExecutable(JavaExec javaExec) {
        JavaLauncher launcher = javaExec.getJavaLauncher().getOrNull();
        if (launcher != null) {
            return launcher.getExecutablePath().getAsFile().toPath().toAbsolutePath().normalize().toString();
        }
        String executable = javaExec.getExecutable();
        if (executable != null) {
            return normalizeExecutable(executable);
        }
        throw new GradleException("Cannot resolve a Java executable for the continuous run launcher.");
    }

    private static String normalizeExecutable(String executable) {
        Path executablePath = Path.of(executable);
        if (executablePath.isAbsolute() || executablePath.getParent() != null) {
            return executablePath.toAbsolutePath().normalize().toString();
        }
        Path resolvedFromPath = resolveFromPath(executable);
        if (resolvedFromPath != null) {
            return resolvedFromPath.toAbsolutePath().normalize().toString();
        }
        return executablePath.toAbsolutePath().normalize().toString();
    }

    private static Path resolveFromPath(String executable) {
        String pathValue = System.getenv("PATH");
        if (pathValue == null || pathValue.isBlank()) {
            return null;
        }
        boolean windows = System.getProperty("os.name", "")
            .toLowerCase(Locale.ROOT)
            .contains("win");
        List<String> candidates = new ArrayList<>();
        candidates.add(executable);
        if (windows && !executable.contains(".")) {
            String pathExt = System.getenv("PATHEXT");
            if (pathExt != null && !pathExt.isBlank()) {
                for (String extension : pathExt.split(java.io.File.pathSeparator)) {
                    if (!extension.isBlank()) {
                        candidates.add(executable + extension);
                    }
                }
            }
        }
        for (String directory : pathValue.split(java.io.File.pathSeparator)) {
            if (directory == null || directory.isBlank()) {
                continue;
            }
            for (String candidate : candidates) {
                Path candidatePath = Path.of(directory, candidate);
                if (Files.isRegularFile(candidatePath) && Files.isExecutable(candidatePath)) {
                    return candidatePath;
                }
            }
        }
        return null;
    }

    private static List<String> createCommandLine(JavaExec javaExec, String ownerToken) {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(resolveExecutable(javaExec));
        commandLine.addAll(javaExec.getAllJvmArgs());
        commandLine.add(OWNER_TOKEN_ARGUMENT_PREFIX + ownerToken);
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

    private static void stopPreviousProcess(Path statePath, String ownerToken, Logger logger) {
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
        ProcessHandle handle = ProcessHandle.of(pid).orElse(null);
        if (handle == null || !handle.isAlive()) {
            deleteState(statePath, logger);
            return;
        }
        if (!matchesRecordedProcess(handle, properties, ownerToken)) {
            logger.warn("Preserving continuous run state file {} because PID {} is still alive but ownership could not be verified.", statePath, pid);
            throw new GradleException("Cannot safely replace the previous continuous run process for " + statePath + " because PID " + pid + " is still alive but ownership could not be verified. The state file was preserved for manual recovery.");
        }
        stopProcess(handle, logger, "Stopping previous continuous run process.");
        deleteState(statePath, logger);
    }

    private static boolean matchesRecordedProcess(ProcessHandle handle, Properties properties, String ownerToken) {
        String expectedCommand = properties.getProperty("command");
        ProcessHandle.Info info = handle.info();
        if (!matchesOwnerToken(handle, info, ownerToken)) {
            return false;
        }
        if (expectedCommand == null) {
            return true;
        }
        return info.command()
            .map(Path::of)
            .map(Path::toAbsolutePath)
            .map(Path::normalize)
            .map(Path::toString)
            .map(expectedCommand::equals)
            .orElse(true);
    }

    private static boolean matchesOwnerToken(ProcessHandle handle, ProcessHandle.Info info, String ownerToken) {
        String expectedArgument = OWNER_TOKEN_ARGUMENT_PREFIX + ownerToken;
        String[] arguments = info.arguments().orElse(null);
        if (containsExpectedArgument(arguments, expectedArgument)) {
            return true;
        }
        String[] procArguments = readProcArguments(handle.pid());
        if (containsExpectedArgument(procArguments, expectedArgument)) {
            return true;
        }
        String processCommandLine = readPsCommandLine(handle.pid());
        return processCommandLine != null && processCommandLine.contains(expectedArgument);
    }

    private static boolean containsExpectedArgument(String[] arguments, String expectedArgument) {
        if (arguments == null) {
            return false;
        }
        for (String argument : arguments) {
            if (expectedArgument.equals(argument)) {
                return true;
            }
        }
        return false;
    }

    private static String[] readProcArguments(long pid) {
        Path cmdlinePath = Path.of("/proc", Long.toString(pid), "cmdline");
        if (!Files.isRegularFile(cmdlinePath)) {
            return null;
        }
        try {
            byte[] contents = Files.readAllBytes(cmdlinePath);
            if (contents.length == 0) {
                return new String[0];
            }
            List<String> arguments = new ArrayList<>();
            int start = 0;
            for (int i = 0; i < contents.length; i++) {
                if (contents[i] == 0) {
                    if (i > start) {
                        arguments.add(new String(contents, start, i - start, StandardCharsets.UTF_8));
                    }
                    start = i + 1;
                }
            }
            if (start < contents.length) {
                arguments.add(new String(contents, start, contents.length - start, StandardCharsets.UTF_8));
            }
            return arguments.toArray(String[]::new);
        } catch (IOException ignored) {
            return null;
        }
    }

    private static String readPsCommandLine(long pid) {
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return null;
        }
        Process process = null;
        try {
            process = new ProcessBuilder("ps", "-p", Long.toString(pid), "-o", "command=").start();
            String output;
            try (InputStream inputStream = process.getInputStream()) {
                output = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
            if (!process.waitFor(1, TimeUnit.SECONDS) || process.exitValue() != 0 || output.isBlank()) {
                return null;
            }
            return output;
        } catch (IOException | InterruptedException ignored) {
            if (ignored instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
        }
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

    private static void writeState(Path statePath, String command, String ownerToken, ProcessHandle handle) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("pid", String.valueOf(handle.pid()));
        properties.setProperty("command", normalizeExecutable(command));
        properties.setProperty("ownerToken", ownerToken);
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

    private static String ownerToken(Path statePath) {
        return UUID.nameUUIDFromBytes(statePath.toString().getBytes(StandardCharsets.UTF_8)).toString();
    }
}
