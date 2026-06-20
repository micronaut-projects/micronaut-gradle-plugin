package io.micronaut.gradle

import org.gradle.api.plugins.JavaPlugin
import spock.lang.Specification
import spock.lang.Unroll

class MicronautTestRuntimeSpec extends Specification {

    @Unroll
    def "parse maps #runtime to the shared junit runtime"() {
        expect:
        MicronautTestRuntime.parse(runtime) == MicronautTestRuntime.JUNIT_5

        where:
        runtime << ["junit", "junit5", "junit_5", "junit6", "junit_6"]
    }

    def "junit runtime keeps junit platform dependencies"() {
        expect:
        MicronautTestRuntime.JUNIT_5.usingJunitPlatform
        MicronautTestRuntime.JUNIT_5.dependencies.get(JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME) == [
            "org.junit.jupiter:junit-jupiter-api",
            "io.micronaut.test:micronaut-test-junit5"
        ]
        MicronautTestRuntime.JUNIT_5.dependencies.get(JavaPlugin.TEST_RUNTIME_ONLY_CONFIGURATION_NAME) == [
            "org.junit.jupiter:junit-jupiter-engine",
            "org.junit.platform:junit-platform-launcher"
        ]
    }
}
