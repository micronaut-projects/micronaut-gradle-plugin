package io.micronaut.gradle;

import org.gradle.api.plugins.JavaPlugin;

import java.util.*;

/**
 * An enum with the different supported test runtimes.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public enum MicronautTestRuntime {
    /**
     * JUnit 5.
     */
    JUNIT_5(mapOf(
            JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME,
            Arrays.asList("org.junit.jupiter:junit-jupiter-api", "io.micronaut.test:micronaut-test-junit5"),
            JavaPlugin.TEST_RUNTIME_ONLY_CONFIGURATION_NAME,
            Collections.singletonList("org.junit.jupiter:junit-jupiter-engine")
    )),
    /**
     * Spock 2.
     */
    SPOCK_2(mapOf(
    JavaPlugin.TEST_COMPILE_ONLY_CONFIGURATION_NAME,
            Collections.singletonList("io.micronaut:micronaut-inject-groovy"),
            JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME,
            Arrays.asList("org.spockframework:spock-core", "io.micronaut.test:micronaut-test-spock")
    )),

    /**
     * No test runtime.
     */
    NONE;

    private final Map<String, List<String>> implementation;

    MicronautTestRuntime(String... dependencies) {
        this.implementation = Collections.singletonMap(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, Arrays.asList(dependencies));
    }

    MicronautTestRuntime(Map<String, List<String>> implementation) {
        this.implementation = implementation;
    }

    public Map<String, List<String>> getDependencies() {
        return implementation;
    }

    private  static Map<String, List<String>> mapOf(Object... values) {
        int len = values.length;
        if (len % 2 != 0) {
            throw new IllegalArgumentException("Number of arguments should be an even number representing the keys and values");
        }

        Map<String, List<String>> answer = new LinkedHashMap<>(len / 2);
        int i = 0;
        while (i < values.length - 1) {
            answer.put(values[i++].toString(), (List<String>) values[i++]);
        }
        return answer;
    }
}
