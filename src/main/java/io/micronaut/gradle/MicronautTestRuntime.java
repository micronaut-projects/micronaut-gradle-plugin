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
    JUNIT_5(MicronautExtension.mapOf(
            JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME,
            Arrays.asList("org.junit.jupiter:junit-jupiter-api", "io.micronaut.test:micronaut-test-junit5"),
            JavaPlugin.TEST_RUNTIME_ONLY_CONFIGURATION_NAME,
            Collections.singletonList("org.junit.jupiter:junit-jupiter-engine")
    )),
    /**
     * Spock 2.
     */
    SPOCK_2(MicronautExtension.mapOf(
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
}
