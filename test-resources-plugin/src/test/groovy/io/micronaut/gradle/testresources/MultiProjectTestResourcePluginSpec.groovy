package io.micronaut.gradle.testresources

import io.micronaut.gradle.AbstractGradleBuildSpec
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Requires

@Requires({ !os.windows })
class MultiProjectTestResourcePluginSpec extends AbstractGradleBuildSpec {

    def "can share a test resources service in a multiproject"() {
        withSample("test-resources/multiproject")

        when:
        def result = build 'test'

        then:
        result.task(':testresources:internalStartTestResourcesService').outcome == TaskOutcome.SUCCESS
        result.task(':app1:test').outcome == TaskOutcome.SUCCESS
        result.task(':app2:test').outcome == TaskOutcome.SUCCESS
        result.task(':app3:test').outcome == TaskOutcome.SUCCESS
        result.output.contains "Loaded 4 test resources resolvers"
        result.output.contains "demo.GreetingTestResource"
        result.output.contains "io.micronaut.testresources.testcontainers.GenericTestContainerProvider"
        result.output.contains "io.micronaut.testresources.mysql.MySQLTestResourceProvider"
        result.output.contains "io.micronaut.testresources.postgres.PostgreSQLTestResourceProvider"
    }

}
