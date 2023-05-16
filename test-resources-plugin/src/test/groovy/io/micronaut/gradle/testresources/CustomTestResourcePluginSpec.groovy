package io.micronaut.gradle.testresources

import io.micronaut.gradle.AbstractGradleBuildSpec
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.Requires

@Requires({ !os.windows })
class CustomTestResourcePluginSpec extends AbstractGradleBuildSpec {

    def "can implement a custom test resource using the testResources source set"() {
        withSample("test-resources/custom-test-resource")

        when:
        def result = build 'test', '-S'

        then:
        result.task(':test').outcome == TaskOutcome.SUCCESS
        result.output.contains "Loaded 2 test resources resolvers"
        result.output.contains "demo.GreetingTestResource"
        result.output.contains "io.micronaut.testresources.testcontainers.GenericTestContainerProvider"
    }

    @Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/619")
    def "test task is not up-to-date when custom test resource is updated"() {
        withSample("test-resources/custom-test-resource")

        when:
        def result = build 'test'

        then:
        result.task(':test').outcome == TaskOutcome.SUCCESS
        result.output.contains "Loaded 2 test resources resolvers"
        result.output.contains "demo.GreetingTestResource"
        result.output.contains "io.micronaut.testresources.testcontainers.GenericTestContainerProvider"

        when:
        def ctr = file("src/testResources/java/demo/GreetingTestResource.java")
        ctr.text = ctr.text.replace("hello", "Hola")

        then:
        fails 'test'

    }

}
