package io.micronaut.gradle.testresources

import io.micronaut.gradle.AbstractGradleBuildSpec
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Requires

@Requires({ !os.windows })
class CustomTestResourcePluginSpec extends AbstractGradleBuildSpec {

    def "can implement a custom test resource using the testResources source set"() {
        withSample("test-resources/custom-test-resource")

        when:
        def result = build 'test'

        then:
        result.task(':test').outcome == TaskOutcome.SUCCESS
        result.output.contains "Loaded 2 test resources resolvers"
        result.output.contains "demo.GreetingTestResource"
        result.output.contains "io.micronaut.testresources.testcontainers.GenericTestContainerProvider"
    }

}
