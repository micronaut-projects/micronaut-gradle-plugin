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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
     * the application exits with status 0 once it has started and warmed up.
     */
    static final String TRAINING_ENABLED_PROPERTY = "micronaut.application.training.enabled";

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
    private static final Pattern GC_FLAG = Pattern.compile("-XX:\\+Use(Serial|Parallel|ParallelOld|G1|Z|Shenandoah|Epsilon|ConcMarkSweep)GC");
    private static final Pattern INVALID_PATH_CHARACTERS = Pattern.compile("[\\s,\\p{Cntrl}]");

    private JdkAotCacheSupport() {
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
        for (String path : options.getTrainingPaths().get()) {
            if (!path.startsWith("/") || INVALID_PATH_CHARACTERS.matcher(path).find()) {
                throw new GradleException("Invalid JDK AOT cache training path '" + path + "': a training path starts with '/' and contains no whitespace and no comma");
            }
        }
        if (options.getTrainingTimeout().get() <= 0) {
            throw new GradleException("The JDK AOT cache training timeout must be positive, but it is " + options.getTrainingTimeout().get());
        }
        if (options.getStrictProbes().get() < 0) {
            throw new GradleException("The number of JDK AOT cache strict probes cannot be negative, but it is " + options.getStrictProbes().get());
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
     * @param trainingRunSwitch whether the application's Micronaut core has the training run switch
     * @return the command
     */
    static List<String> trainingCommand(String workDir,
                                        List<String> args,
                                        List<Integer> exposedPorts,
                                        JdkAotCacheOptions options,
                                        boolean trainingRunSwitch) {
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
        if (trainingRunSwitch) {
            // The application warms itself up and exits with 0
            command.add("--training-run");
            java.add("-D" + TRAINING_ENABLED_PROPERTY + "=true");
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
     * Whether the application's Micronaut core has the training run switch. The first JAR that contains
     * {@code io.micronaut.runtime.Micronaut} decides, as it does on the class path: the switch is there when
     * that class refers to the switch's property.
     *
     * @param classpath the runtime class path of the application
     * @return true if the application can train with the switch
     */
    static boolean hasTrainingRunSwitch(Iterable<File> classpath) {
        byte[] property = TRAINING_ENABLED_PROPERTY.getBytes(StandardCharsets.UTF_8);
        for (File file : classpath) {
            if (!file.isFile() || !file.getName().endsWith(".jar")) {
                continue;
            }
            try (var jar = new ZipFile(file)) {
                ZipEntry entry = jar.getEntry(MICRONAUT_CLASS);
                if (entry != null) {
                    try (InputStream in = jar.getInputStream(entry)) {
                        // The property is a compile-time constant, so its UTF-8 bytes are in the class's constant pool
                        return contains(in.readAllBytes(), property);
                    }
                }
            } catch (IOException e) {
                // Not a readable JAR, so it does not provide Micronaut core
            }
        }
        return false;
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
