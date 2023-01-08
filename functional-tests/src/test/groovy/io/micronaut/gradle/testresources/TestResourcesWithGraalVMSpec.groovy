package io.micronaut.gradle.testresources

import io.micronaut.gradle.AbstractGradleBuildSpec
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Requires

@Requires({ AbstractGradleBuildSpec.graalVmAvailable && !os.windows })
class TestResourcesWithGraalVMSpec extends AbstractTestResourcesSpec {

    def setup() {
//        allowMavenLocal = true
    }

    def "runs native tests"() {
        withSample("test-resources/data-mysql")
        buildFile.text = buildFile.text.replace("""plugins {
    id("io.micronaut.minimal.application")
    id("io.micronaut.test-resources")
}""", """plugins {
    id("io.micronaut.minimal.application")
    id("io.micronaut.test-resources")
    id("io.micronaut.graalvm")
}

graalvmNative.binaries.all {
    runtimeArgs.add("-DinterruptStartup=true")
}

""")

        when:
        def result = build 'nativeTest'

        then:
        result.task(':nativeTest').outcome == TaskOutcome.SUCCESS
        result.output.contains "Loaded 2 test resources resolvers"
        result.output.contains "io.micronaut.testresources.mysql.MySQLTestResourceProvider"
        result.output.contains "io.micronaut.testresources.testcontainers.GenericTestContainerProvider"
    }

    def "integrates with test resources without further configuration"() {
        withSample("test-resources/data-mysql")
        buildFile.text = buildFile.text.replace("""plugins {
    id("io.micronaut.minimal.application")
    id("io.micronaut.test-resources")
}""", """plugins {
    id("io.micronaut.minimal.application")
    id("io.micronaut.test-resources")
    id("io.micronaut.graalvm")
}

graalvmNative.binaries.all {
    runtimeArgs.add("-DinterruptStartup=true")
}

""")

        when:
        def result = build 'nativeRun'

        then:
        result.task(':nativeRun').outcome == TaskOutcome.SUCCESS
        result.output.contains "Loaded 2 test resources resolvers"
        result.output.contains "io.micronaut.testresources.mysql.MySQLTestResourceProvider"
        result.output.contains "io.micronaut.testresources.testcontainers.GenericTestContainerProvider"
    }
}
