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
            Arrays.asList(
                    "org.spockframework:spock-core",
                    "io.micronaut.test:micronaut-test-spock",
                    "org.apache.groovy:groovy"
            )
    )),
    /**
     * Kotest 4.
     */
    KOTEST_4(MicronautExtension.mapOf(
            MicronautKotlinSupport.KOTLIN_PROCESSORS,
            Collections.singletonList("io.micronaut:micronaut-inject-java"),
            JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME,
            Arrays.asList(
                    "io.mockk:mockk",
                    "io.micronaut.test:micronaut-test-kotest",
                    "io.kotest:kotest-assertions-core-jvm"
            ),
            JavaPlugin.TEST_RUNTIME_ONLY_CONFIGURATION_NAME,
            Collections.singletonList("io.kotest:kotest-runner-junit5-jvm")
    )),

    /**
     * Kotest 5.
     */
    KOTEST_5(MicronautExtension.mapOf(
            MicronautKotlinSupport.KOTLIN_PROCESSORS,
            Collections.singletonList("io.micronaut:micronaut-inject-java"),
            JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME,
            Arrays.asList(
                    "io.mockk:mockk",
                    "io.micronaut.test:micronaut-test-kotest5",
                    "io.kotest:kotest-assertions-core-jvm"
            ),
            JavaPlugin.TEST_RUNTIME_ONLY_CONFIGURATION_NAME,
            Collections.singletonList("io.kotest:kotest-runner-junit5-jvm")
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

    public static MicronautTestRuntime parse(String str) {
        if (str != null) {
            str = str.toUpperCase(Locale.ENGLISH);
            switch (str) {
                case "JUNIT":
                case "JUNIT5":
                case "JUNIT_5":
                    return MicronautTestRuntime.JUNIT_5;
                case "SPOCK":
                case "SPOCK2":
                case "SPOCK_2":
                    return MicronautTestRuntime.SPOCK_2;
                case "KOTEST":
                case "KOTEST4":
                case "KOTEST_4":
                    return MicronautTestRuntime.KOTEST_4;
                case "KOTEST5":
                case "KOTEST_5":
                    return MicronautTestRuntime.KOTEST_5;
            }
        }
        return MicronautTestRuntime.NONE;
    }
}
