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

    def "runs the application with test resources support in a multiproject consumer"() {
        withSample("test-resources/multiproject")

        // The sample Application.java already checks System.getProperty("interruptStartup"),
        // but the build.gradle doesn't forward the system property from Gradle to the JVM.
        file("app1/build.gradle") << """
            tasks.withType(JavaExec).configureEach {
                if (System.getProperty("interruptStartup")) {
                    systemProperty "interruptStartup", "true"
                }
            }
        """

        when:
        def result = build '-DinterruptStartup=true', ':app1:run'

        then:
        result.task(':testresources:internalStartTestResourcesService').outcome == TaskOutcome.SUCCESS
        result.task(':app1:run').outcome == TaskOutcome.SUCCESS
        result.output.contains "Loaded 4 test resources resolvers"
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
            assert result.output.count("Creating container for image: testcontainers/ryuk") == 2
        }
    }

}
