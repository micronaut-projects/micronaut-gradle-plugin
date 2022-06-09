package io.micronaut.gradle.testresources

import io.micronaut.gradle.AbstractGradleBuildSpec
import org.gradle.testkit.runner.TaskOutcome

class ApplicationTestResourcesPluginSpec extends AbstractGradleBuildSpec {

    def "integrates with test resources without further configuration"() {
        withSample("test-resources/data-mysql")

        when:
        def result = build 'test'

        then:
        result.task(':test').outcome == TaskOutcome.SUCCESS
        result.output.contains "Loaded 2 test resources resolvers: io.micronaut.testresources.mysql.MySQLTestResourceProvider, io.micronaut.testresources.testcontainers.GenericTestContainerProvider"
    }

    def "fails if test resources support is disabled"() {
        withSample("test-resources/data-mysql")
        withTestResourcesConfiguration """
            enabled = false
        """

        when:
        def result = fails 'test'

        then:
        result.task(":startTestResourcesService").outcome == TaskOutcome.SKIPPED
        result.task(':test').outcome == TaskOutcome.FAILED
        !result.output.contains("Loaded 1 test resources resolvers: io.micronaut.testresources.testcontainers.GenericTestContainerProvider")
    }

    def "fails if test resources inference is disabled"() {
        withSample("test-resources/data-mysql")
        withTestResourcesConfiguration """
            inferClasspath = false
        """

        when:
        def result = fails 'test'

        then:
        result.task(":startTestResourcesService").outcome == TaskOutcome.SUCCESS
        result.task(':test').outcome == TaskOutcome.FAILED
        result.output.contains "Loaded 1 test resources resolvers: io.micronaut.testresources.testcontainers.GenericTestContainerProvider"
    }

    def "passes using explicit configuration"() {
        withSample("test-resources/data-mysql")
        withTestResourcesConfiguration """
            inferClasspath = false
            additionalModules.addAll("jdbc-mysql", "kafka")
        """
        withDependencies """
            testresources "mysql:mysql-connector-java"
        """

        when:
        def result = build 'test'

        then:
        result.task(':test').outcome == TaskOutcome.SUCCESS
        result.output.contains "Loaded 3 test resources resolvers: io.micronaut.testresources.kafka.KafkaTestResourceProvider, io.micronaut.testresources.mysql.MySQLTestResourceProvider, io.micronaut.testresources.testcontainers.GenericTestContainerProvider"
    }

    def "runs the application with test resources support"() {
        withSample("test-resources/data-mysql")

        when:
        def result = build '-DinterruptStartup=true', 'run'

        then:
        result.task(':run').outcome == TaskOutcome.SUCCESS
        result.output.contains "Loaded 2 test resources resolvers: io.micronaut.testresources.mysql.MySQLTestResourceProvider, io.micronaut.testresources.testcontainers.GenericTestContainerProvider"
    }

    private void withTestResourcesConfiguration(String configuration) {
        updateBuildFile("TEST_RESOURCES_MARKER", configuration)
    }

    private void withDependencies(String dependencies) {
        updateBuildFile("TEST_DEPENDENCIES_MARKER", dependencies)
    }

    private void updateBuildFile(String marker, String configuration) {
        StringBuilder sb = new StringBuilder()
        buildFile.readLines().each {
            if (it.contains(marker)) {
                sb.append(configuration)
            } else {
                sb.append(it).append("\n")
            }
        }
        buildFile.text = sb.toString()
    }
}
