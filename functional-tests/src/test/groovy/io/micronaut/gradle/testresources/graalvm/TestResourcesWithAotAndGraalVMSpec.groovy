package io.micronaut.gradle.testresources.graalvm

import io.micronaut.gradle.AbstractGradleBuildSpec
import io.micronaut.gradle.testresources.AbstractTestResourcesSpec
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Requires

@Requires({ AbstractGradleBuildSpec.graalVmAvailable && !os.windows })
class TestResourcesWithAotAndGraalVMSpec extends AbstractTestResourcesSpec {

    def "runs optimized binary"() {
        withSample("test-resources/data-mysql")
        buildFile.text = buildFile.text.replace("""plugins {
    id("io.micronaut.minimal.application")
    id("io.micronaut.test-resources")
}""", """plugins {
    id("io.micronaut.minimal.application")
    id("io.micronaut.test-resources")
    id("io.micronaut.aot")
    id("io.micronaut.graalvm")
}

graalvmNative.binaries.all {
    runtimeArgs.add("-DinterruptStartup=true")
}

micronaut {
    aot {
        deduceEnvironment = true
        optimizeServiceLoading = true
    }
}
""")

        when:
        def result = build 'nativeOptimizedRun', '-i'

        then:
        result.task(':nativeOptimizedRun').outcome == TaskOutcome.SUCCESS
        result.output.contains "Loaded 2 test resources resolvers"
        result.output.contains "io.micronaut.testresources.mysql.MySQLTestResourceProvider"
        result.output.contains "io.micronaut.testresources.testcontainers.GenericTestContainerProvider"
    }
}
