package io.micronaut.gradle;

import org.gradle.api.plugins.JavaPlugin;
import org.gradle.util.CollectionUtils;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

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
            JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME, "org.junit.jupiter:junit-jupiter-api",
            JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME, "io.micronaut.test:micronaut-test-junit5",
            JavaPlugin.TEST_RUNTIME_ONLY_CONFIGURATION_NAME, "org.junit.jupiter:junit-jupiter-engine"
    )),
    /**
     * Spock 2.
     */
    SPOCK_2(mapOf(
    JavaPlugin.TEST_COMPILE_ONLY_CONFIGURATION_NAME, "io.micronaut:micronaut-inject-groovy",
            JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME, "org.spockframework:spock-core",
            JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME, "io.micronaut.test:micronaut-test-spock"
    )),

    /**
     * No test runtime.
     */
    NONE;

    private final Map<String, String> implementation;

    MicronautTestRuntime(String... dependencies) {
        this.implementation = Arrays.stream(dependencies).collect(
                Collectors.toMap((v)-> JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, (v) -> v)
        );
    }

    MicronautTestRuntime(Map<String, String> implementation) {
        this.implementation = implementation;
    }

    public Map<String, String> getDependencies() {
        return implementation;
    }

    private  static Map<String, String> mapOf(String... values) {
        int len = values.length;
        if (len % 2 != 0) {
            throw new IllegalArgumentException("Number of arguments should be an even number representing the keys and values");
        }

        Map<String, String> answer = new LinkedHashMap<>(len / 2);
        int i = 0;
        while (i < values.length - 1) {
            answer.put(values[i++], values[i++]);
        }
        return answer;
    }
}
