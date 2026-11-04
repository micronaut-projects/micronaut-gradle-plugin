package io.micronaut.gradle.testresources

import io.micronaut.gradle.AbstractGradleBuildSpec
import org.gradle.util.GradleVersion
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Requires

@Requires({ !os.windows })
class MultiProjectTestResourcePluginSpec extends AbstractGradleBuildSpec {

    @Requires({ GradleVersion.current() >= GradleVersion.version("9.7") })
    def "supports the configuration cache with isolated projects"() {
        withSample("test-resources/isolated-multiproject")
        buildFile.text = ""
        settingsFile << """
            dependencyResolutionManagement {
                $repositoriesBlock
            }
        """

        when:
        def result = build '--isolated-projects', '--configuration-cache', ':app1:internalStartTestResourcesService', ':app2:help', '--dry-run'

        then:
        def output = result.output.toLowerCase()
        !output.contains('cannot access the tasks in the task graph that were created by other projects') &&
                (output.contains('configuration cache entry stored') ||
                        output.contains('support for using a java agent with testkit builds is not yet implemented with the configuration cache'))
    }

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

    def "can use independent test resource services"() {
        withSample("test-resources/isolated-multiproject")

        when:
        def result = build 'test'

        then:
        result.task(':app1:internalStartTestResourcesService').outcome == TaskOutcome.SUCCESS
        result.task(':app1:test').outcome == TaskOutcome.SUCCESS
        result.task(':app2:internalStartTestResourcesService').outcome == TaskOutcome.SUCCESS
        result.task(':app2:test').outcome == TaskOutcome.SUCCESS
        result.task(':app3:internalStartTestResourcesService').outcome == TaskOutcome.SUCCESS
        result.task(':app3:test').outcome == TaskOutcome.SUCCESS
        if (System.getenv("TESTCONTAINERS_RYUK_DISABLED") != "true") {
            assert result.output.count("Creating container for image: testcontainers/ryuk") == 3
        }
    }

    def "detects transitive runtime classpath modules from project dependencies"() {
        withSample("test-resources/transitive-runtime-classpath")

        when:
        def result = build 'test'

        then:
        result.task(':app:internalStartTestResourcesService').outcome == TaskOutcome.SUCCESS
        result.task(':app:test').outcome == TaskOutcome.SUCCESS
        result.output.contains "io.micronaut.testresources.kafka.KafkaTestResourceProvider"
        result.output.contains "io.micronaut.testresources.redis.RedisTestResourceProvider"
        result.output.contains "io.micronaut.testresources.testcontainers.GenericTestContainerProvider"
        result.output.count("io.micronaut.testresources.redis.RedisTestResourceProvider") == 1
    }

}
