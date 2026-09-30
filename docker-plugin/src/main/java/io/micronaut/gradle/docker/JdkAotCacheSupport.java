/*
 * Copyright 2003-2026 the original author or authors.
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
package io.micronaut.gradle.docker;

import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Builds the JDK AOT cache training of the generated JVM Dockerfiles: the {@code RUN} that trains the
 * cache with the plugin's training script, and the flags that the training run and the {@code ENTRYPOINT}
 * share.
 */
final class JdkAotCacheSupport {

    /**
     * The training script, as a resource of this package and as a path in the Docker context.
     */
    static final String TRAINING_SCRIPT = "jdk-aot-cache/train.sh";

    /**
     * The cache file, relative to the image's working directory.
     */
    static final String CACHE_FILE = "application.aot";

    /**
     * The training run switch of Micronaut core (micronaut-projects/micronaut-core#13391): with it,
     * the application exits with status 0 once the training run is done.
     */
    static final String TRAINING_ENABLED_PROPERTY = "micronaut.application.training.enabled";

    /**
     * The mode of a training run of Micronaut core, {@link #MODE_LOAD} or {@link #MODE_START}.
     * A core that has the switch does not necessarily have the mode.
     */
    static final String TRAINING_MODE_PROPERTY = "micronaut.application.training.mode";

    /**
     * The training mode that loads the bean definitions and does not start the application.
     */
    static final String MODE_LOAD = "load";

    /**
     * The training mode that starts the application and requests the training paths.
     */
    static final String MODE_START = "start";

    /**
     * The GET paths of the training run switch's warm-up, as a comma-separated list.
     */
    static final String TRAINING_WARMUP_PATHS_PROPERTY = "micronaut.application.training.warmup.paths";

    /**
     * The collector pinned when the JVM arguments select none. Builders usually get G1 by ergonomics
     * while small pods get SerialGC, and a cache records the collector that trained it.
     */
    static final String DEFAULT_GC_FLAG = "-XX:+UseG1GC";

    private static final String MICRONAUT_CLASS = "io/micronaut/runtime/Micronaut.class";
    private static final String APPLICATION_CONFIGURATION_CLASS = "io/micronaut/runtime/ApplicationConfiguration.class";
    private static final Pattern GC_FLAG = Pattern.compile("-XX:\\+Use(Serial|Parallel|ParallelOld|G1|Z|Shenandoah|Epsilon|ConcMarkSweep)GC");
    private static final Pattern INVALID_PATH_CHARACTERS = Pattern.compile("[\\s,\\p{Cntrl}]");

    private JdkAotCacheSupport() {
    }

    /**
     * What the application's Micronaut core offers for a training run.
     */
    enum TrainingRunSupport {
        /**
         * No training run switch: the training script starts the application, waits for it, sends the
         * requests and stops it.
         */
        NONE,
        /**
         * The training run switch, which starts the application, warms it up and exits.
         */
        SWITCH,
        /**
         * The training run switch and its modes, of which {@code load} does not start the application.
         */
        MODE
    }

    /**
     * Sets the default values of the options.
     *
     * @param options the options
     */
    static void configureDefaults(JdkAotCacheOptions options) {
        options.getEnabled().convention(false);
        options.getTrainingTimeout().convention(120);
        options.getCompatibleOopCompression().convention(true);
        options.getStrictProbes().convention(0);
    }

    /**
     * The flags that the training run and the {@code ENTRYPOINT} both put after {@code java} and before
     * the JVM arguments: the collector, unless the arguments select one.
     *
     * @param args the JVM arguments of the image
     * @return the shared flags
     */
    static List<String> sharedJvmFlags(List<String> args) {
        if (args.stream().anyMatch(arg -> GC_FLAG.matcher(arg).matches())) {
            return List.of();
        }
        return List.of(DEFAULT_GC_FLAG);
    }

    /**
     * Checks the options that are known once the project is evaluated.
     *
     * @param options the options
     */
    static void validate(JdkAotCacheOptions options) {
        List<String> trainingPaths = options.getTrainingPaths().get();
        for (String path : trainingPaths) {
            if (!path.startsWith("/") || INVALID_PATH_CHARACTERS.matcher(path).find()) {
                throw new GradleException("Invalid JDK AOT cache training path '" + path + "': a training path starts with '/' and contains no whitespace and no comma");
            }
        }
        String trainingMode = requestedTrainingMode(options);
        if (!trainingPaths.isEmpty() && !MODE_START.equals(trainingMode)) {
            // Whatever the Micronaut version: a build that sends requests says that it starts the application
            throw new GradleException("The JDK AOT cache training paths are only requested by a training run that starts the application, but the training mode is "
                + (trainingMode == null ? "not set" : "'" + trainingMode + "'")
                + ": set trainingMode to '" + MODE_START + "', or remove the training paths");
        }
        if (options.getTrainingTimeout().get() <= 0) {
            throw new GradleException("The JDK AOT cache training timeout must be positive, but it is " + options.getTrainingTimeout().get());
        }
        if (options.getStrictProbes().get() < 0) {
            throw new GradleException("The number of JDK AOT cache strict probes cannot be negative, but it is " + options.getStrictProbes().get());
        }
    }

    /**
     * The training mode that the build asks for.
     *
     * @param options the options
     * @return {@link #MODE_LOAD}, {@link #MODE_START}, or null if the build leaves the choice to the plugin
     */
    static String requestedTrainingMode(JdkAotCacheOptions options) {
        String trainingMode = options.getTrainingMode().getOrNull();
        if (trainingMode == null) {
            return null;
        }
        // In any case, as Micronaut core reads its own property
        String normalized = trainingMode.trim().toLowerCase(Locale.ROOT);
        if (!MODE_LOAD.equals(normalized) && !MODE_START.equals(normalized)) {
            throw new GradleException("Invalid JDK AOT cache training mode '" + trainingMode + "': the training mode is '" + MODE_LOAD + "' or '" + MODE_START + "'");
        }
        return normalized;
    }

    /**
     * The mode of the training run. Unless the build asks for one, the run does not start the application
     * if the application's Micronaut core can train without starting it, because an image build has none
     * of the services that an application needs to start.
     *
     * @param options the options
     * @param support what the application's Micronaut core offers for a training run
     * @return {@link #MODE_LOAD} or {@link #MODE_START}
     */
    static String trainingMode(JdkAotCacheOptions options, TrainingRunSupport support) {
        String requested = requestedTrainingMode(options);
        if (requested == null) {
            return support == TrainingRunSupport.MODE ? MODE_LOAD : MODE_START;
        }
        if (MODE_LOAD.equals(requested) && support != TrainingRunSupport.MODE) {
            // Starting the application instead would do what the build asks not to do
            throw new GradleException("The JDK AOT cache training mode is '" + MODE_LOAD + "', but the application's Micronaut version has no such mode: "
                + "it needs a Micronaut Core with the " + TRAINING_MODE_PROPERTY + " property. "
                + "Upgrade Micronaut, or remove trainingMode to train with a run that starts the application");
        }
        return requested;
    }

    /**
     * Says which training run the image build does. A run that starts the application although the build
     * did not ask for it is reported at lifecycle level, with what the build can do about it, the others
     * at info level.
     *
     * @param logger the logger of the Dockerfile task
     * @param options the options
     * @param support what the application's Micronaut core offers for a training run
     */
    static void logTrainingMode(Logger logger, JdkAotCacheOptions options, TrainingRunSupport support) {
        if (MODE_LOAD.equals(trainingMode(options, support))) {
            logger.info("JDK AOT cache: the training run loads the bean definitions of the application and does not start it ({}={})", TRAINING_MODE_PROPERTY, MODE_LOAD);
        } else if (requestedTrainingMode(options) == null) {
            logger.lifecycle("JDK AOT cache: the application's Micronaut version has no '{}' training mode ({}), so the training run starts the application while the image is built, "
                    + "where the services it needs to start must be available. "
                    + "Use a Micronaut version that has the '{}' mode to train without starting the application, or set trainingMode = '{}' to confirm this run",
                MODE_LOAD, TRAINING_MODE_PROPERTY, MODE_LOAD, MODE_START);
        } else {
            logger.info("JDK AOT cache: the training run starts the application in the image build");
        }
    }

    /**
     * Builds the command of the training {@code RUN}: the training script, its options, then the command
     * that the {@code ENTRYPOINT} runs, without {@code -XX:AOTCache}.
     *
     * @param workDir the working directory of the image
     * @param args the JVM arguments of the image
     * @param exposedPorts the ports exposed by the image, the first one being the application's HTTP port
     * @param options the JDK AOT cache options
     * @param support what the application's Micronaut core offers for a training run
     * @return the command
     */
    static List<String> trainingCommand(String workDir,
                                        List<String> args,
                                        List<Integer> exposedPorts,
                                        JdkAotCacheOptions options,
                                        TrainingRunSupport support) {
        String trainingMode = trainingMode(options, support);
        List<String> trainingPaths = options.getTrainingPaths().get();
        var command = new ArrayList<String>();
        command.add("bash");
        command.add(workDir + "/" + TRAINING_SCRIPT);
        command.add("--cache");
        command.add(workDir + "/" + CACHE_FILE);
        command.add("--timeout");
        command.add(String.valueOf(options.getTrainingTimeout().get()));
        if (Boolean.TRUE.equals(options.getCompatibleOopCompression().get())) {
            command.add("--compatible-oop-compression");
        }
        int strictProbes = options.getStrictProbes().get();
        if (strictProbes > 0) {
            command.add("--strict-probes");
            command.add(String.valueOf(strictProbes));
        }
        var java = new ArrayList<String>();
        java.add("java");
        if (support != TrainingRunSupport.NONE) {
            // The application ends the training run itself and exits with 0
            command.add("--training-run");
            java.add("-D" + TRAINING_ENABLED_PROPERTY + "=true");
            if (support == TrainingRunSupport.MODE) {
                // Always given, so that nothing in the configuration of the application selects another mode
                java.add("-D" + TRAINING_MODE_PROPERTY + "=" + trainingMode);
            }
            if (!trainingPaths.isEmpty()) {
                java.add("-D" + TRAINING_WARMUP_PATHS_PROPERTY + "=" + String.join(",", trainingPaths));
            }
        } else {
            // The script waits for the HTTP server, sends the requests and stops the application
            if (exposedPorts.isEmpty()) {
                throw new GradleException("The JDK AOT cache training run sends HTTP requests to the first exposed port, but the image exposes no port");
            }
            command.add("--port");
            command.add(String.valueOf(exposedPorts.get(0)));
            for (String path : trainingPaths) {
                command.add("--path");
                command.add(path);
            }
        }
        java.addAll(sharedJvmFlags(args));
        java.addAll(args);
        java.add("-jar");
        java.add(workDir + "/application.jar");
        command.add("--");
        command.addAll(java);
        return command;
    }

    /**
     * Renders a command in the exec form of a Dockerfile instruction, a JSON array of strings.
     *
     * @param command the command
     * @return the exec form
     */
    static String execForm(List<String> command) {
        return command.stream()
            .map(arg -> "\"" + arg.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
            .collect(Collectors.joining(", ", "[", "]"));
    }

    /**
     * What the application's Micronaut core offers for a training run. The first JAR that contains
     * {@code io.micronaut.runtime.Micronaut} decides, as it does on the class path: the switch is there
     * when that class refers to the switch's property, and the modes are there when
     * {@code io.micronaut.runtime.ApplicationConfiguration}, in the same JAR, declares the mode's property.
     *
     * @param classpath the runtime class path of the application
     * @return the support for a training run
     */
    static TrainingRunSupport trainingRunSupport(Iterable<File> classpath) {
        for (File file : classpath) {
            if (!file.isFile() || !file.getName().endsWith(".jar")) {
                continue;
            }
            try (var jar = new ZipFile(file)) {
                if (jar.getEntry(MICRONAUT_CLASS) != null) {
                    // Both properties are compile-time constants, so their UTF-8 bytes are in the constant pool
                    // of the class that uses the first one and of the class that declares the second one
                    if (!entryContains(jar, MICRONAUT_CLASS, TRAINING_ENABLED_PROPERTY)) {
                        return TrainingRunSupport.NONE;
                    }
                    return entryContains(jar, APPLICATION_CONFIGURATION_CLASS, TRAINING_MODE_PROPERTY)
                        ? TrainingRunSupport.MODE
                        : TrainingRunSupport.SWITCH;
                }
            } catch (IOException e) {
                // Not a readable JAR, so it does not provide Micronaut core
            }
        }
        return TrainingRunSupport.NONE;
    }

    private static boolean entryContains(ZipFile jar, String name, String constant) throws IOException {
        ZipEntry entry = jar.getEntry(name);
        if (entry == null) {
            return false;
        }
        try (InputStream in = jar.getInputStream(entry)) {
            return contains(in.readAllBytes(), constant.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static boolean contains(byte[] bytes, byte[] target) {
        for (int i = 0; i <= bytes.length - target.length; i++) {
            if (Arrays.equals(bytes, i, i + target.length, target, 0, target.length)) {
                return true;
            }
        }
        return false;
    }
}
