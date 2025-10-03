package io.micronaut.gradle;

import org.gradle.api.plugins.JavaPlugin;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
            JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME, List.of("org.junit.jupiter:junit-jupiter-api", "io.micronaut.test:micronaut-test-junit5"),
            JavaPlugin.TEST_RUNTIME_ONLY_CONFIGURATION_NAME, List.of("org.junit.jupiter:junit-jupiter-engine", "org.junit.platform:junit-platform-launcher")
    ), true),
    /**
     * Spock 2.
     */
    SPOCK_2(MicronautExtension.mapOf(
            JavaPlugin.TEST_COMPILE_ONLY_CONFIGURATION_NAME,
            Collections.singletonList("io.micronaut:micronaut-inject-groovy"),
            JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME,
            List.of(
                    "org.spockframework:spock-core",
                    "io.micronaut.test:micronaut-test-spock",
                    "org.apache.groovy:groovy"
            )
    ), true),
    /**
     * Kotest 4.
     */
    KOTEST_4(MicronautExtension.mapOf(
            MicronautKotlinSupport.KOTLIN_PROCESSORS,
            Collections.singletonList("io.micronaut:micronaut-inject-java"),
            JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME,
            List.of(
                    "io.mockk:mockk",
                    "io.micronaut.test:micronaut-test-kotest",
                    "io.kotest:kotest-assertions-core-jvm"
            ),
            JavaPlugin.TEST_RUNTIME_ONLY_CONFIGURATION_NAME,
            Collections.singletonList("io.kotest:kotest-runner-junit5-jvm")
    ), true),

    /**
     * Kotest 5.
     */
    KOTEST_5(MicronautExtension.mapOf(
            MicronautKotlinSupport.KOTLIN_PROCESSORS,
            Collections.singletonList("io.micronaut:micronaut-inject-java"),
            JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME,
            List.of(
                    "io.mockk:mockk",
                    "io.micronaut.test:micronaut-test-kotest5",
                    "io.kotest:kotest-assertions-core-jvm"
            ),
            JavaPlugin.TEST_RUNTIME_ONLY_CONFIGURATION_NAME,
            Collections.singletonList("io.kotest:kotest-runner-junit5-jvm")
    ), true),
    /**
     * No test runtime.
     */
    NONE;

    private final Map<String, List<String>> implementation;
    private final boolean usesJunitPlatform;

    MicronautTestRuntime() {
        this.implementation = Collections.singletonMap(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, Collections.emptyList());
        this.usesJunitPlatform = false;
    }

    MicronautTestRuntime(Map<String, List<String>> implementation, boolean usesJunitPlatform) {
        this.implementation = implementation;
        this.usesJunitPlatform = usesJunitPlatform;
    }

    public Map<String, List<String>> getDependencies() {
        return implementation;
    }

    public static MicronautTestRuntime parse(String str) {
        if (str != null) {
            str = str.toUpperCase(Locale.ENGLISH);
            switch (str) {
                case "JUNIT", "JUNIT5", "JUNIT_5" -> {
                    return MicronautTestRuntime.JUNIT_5;
                }
                case "SPOCK", "SPOCK2", "SPOCK_2" -> {
                    return MicronautTestRuntime.SPOCK_2;
                }
                case "KOTEST", "KOTEST4", "KOTEST_4" -> {
                    return MicronautTestRuntime.KOTEST_4;
                }
                case "KOTEST5", "KOTEST_5" -> {
                    return MicronautTestRuntime.KOTEST_5;
                }
            }
        }
        return MicronautTestRuntime.NONE;
    }

    public boolean isUsingJunitPlatform() {
        return usesJunitPlatform;
    }
}
