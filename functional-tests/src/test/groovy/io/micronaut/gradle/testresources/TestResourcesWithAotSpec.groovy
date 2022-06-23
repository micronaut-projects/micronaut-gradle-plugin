package io.micronaut.gradle.testresources


import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf

@IgnoreIf({ os.windows })
class TestResourcesWithAotSpec extends AbstractTestResourcesSpec {

    def "integrates with test resources without further configuration"() {
        withSample("test-resources/data-mysql")
        buildFile.text = buildFile.text.replace("""plugins {
    id("io.micronaut.minimal.application")
    id("io.micronaut.test-resources")
}""", """plugins {
    id("io.micronaut.minimal.application")
    id("io.micronaut.test-resources")
    id("io.micronaut.aot")
}""")

        when:
        def result = build 'optimizedRun', '-DinterruptStartup=true'

        then:
        result.task(':optimizedRun').outcome == TaskOutcome.SUCCESS
        result.output.contains "Loaded 2 test resources resolvers"
        result.output.contains "io.micronaut.testresources.mysql.MySQLTestResourceProvider"
        result.output.contains "io.micronaut.testresources.testcontainers.GenericTestContainerProvider"
    }
}
