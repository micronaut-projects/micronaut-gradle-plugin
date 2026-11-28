package io.micronaut.gradle.testresources

import io.micronaut.gradle.AbstractGradleBuildSpec
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf

import java.net.InetSocketAddress
import java.net.Socket

@IgnoreIf({ os.windows })
class ApplicationTestResourcesPluginSpec extends AbstractGradleBuildSpec {

    def "integrates with test resources without further configuration"() {
        withSample("test-resources/data-mysql")

        when:
        def result = build 'test', '-i'

        then:
        result.task(':test').outcome == TaskOutcome.SUCCESS
        result.output.contains "Loaded 2 test resources resolvers"
        result.output.contains "io.micronaut.testresources.mysql.MySQLTestResourceProvider"
        result.output.contains "io.micronaut.testresources.testcontainers.GenericTestContainerProvider"
        !result.output.contains("io.micronaut.testresources.hivemq.HiveMQTestResourceProvider")
        !result.output.contains("io.micronaut.testresources.neo4j.Neo4jTestResourceProvider")

        when:
        result = build 'test'

        then:
        // Gradle TestKit doesn't always report UP-TO-DATE tasks in BuildResult on newer Gradle versions.
        def testTask = result.task(':test')
        testTask == null || testTask.outcome == TaskOutcome.UP_TO_DATE
    }

    def "creates temp test-resources directory when running 'clean build'"() {
        withSample("test-resources/data-mysql")

        when:
        def result = build 'clean', 'build'

        then:
        result.task(':test').outcome == TaskOutcome.SUCCESS
        result.output.contains "Loaded 2 test resources resolvers"
        result.output.contains "io.micronaut.testresources.mysql.MySQLTestResourceProvider"
        result.output.contains "io.micronaut.testresources.testcontainers.GenericTestContainerProvider"
    }

    def "fails if test resources support is disabled"() {
        withSample("test-resources/data-mysql")
        withTestResourcesConfiguration """
            enabled = false
        """

        when:
        def result = fails 'test'

        then:
        result.task(":internalStartTestResourcesService").outcome == TaskOutcome.SKIPPED
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
        result.task(":internalStartTestResourcesService").outcome == TaskOutcome.SUCCESS
        result.task(':test').outcome == TaskOutcome.FAILED
        result.output.contains "Loaded 1 test resources resolvers: io.micronaut.testresources.testcontainers.GenericTestContainerProvider"
    }

    def "passes using explicit configuration"() {
        withSample("test-resources/data-mysql")
        
        withTestResourcesConfiguration """
            inferClasspath = false
            additionalModules.addAll(JDBC_MYSQL, KAFKA)
        """
        withDependencies """
            testResourcesService "mysql:mysql-connector-java"
        """

        when:
        def result = build 'test'

        then:
        result.task(':test').outcome == TaskOutcome.SUCCESS
        result.output.contains "Loaded 6 test resources resolvers"
        result.output.contains "io.micronaut.testresources.kafka.KafkaSchemaRegistryTestResourceProvider"
        result.output.contains "io.micronaut.testresources.kafka.KafkaTestResourceProvider"
        result.output.contains "io.micronaut.testresources.kafka.KafkaKsqlDbTestResourceProvider"
        result.output.contains "io.micronaut.testresources.kafka.KafkaConnectTestResourceProvider"
        result.output.contains "io.micronaut.testresources.mysql.MySQLTestResourceProvider"
        result.output.contains "io.micronaut.testresources.testcontainers.GenericTestContainerProvider"
    }

    def "wires test resources into all test tasks by default"() {
        withSample("test-resources/data-mysql")
        withJvmTestSuite()
        withTaskSelectionVerification(true, true)

        when:
        def result = build 'verifyTestResourcesTaskSelection'

        then:
        result.task(':verifyTestResourcesTaskSelection').outcome == TaskOutcome.SUCCESS
    }

    def "wires test resources only into selected test tasks"() {
        withSample("test-resources/data-mysql")
        withTestResourcesConfiguration '''
            testTasks.add("integrationTest")
        '''
        withJvmTestSuite()
        withTaskSelectionVerification(false, true)

        when:
        def result = build 'verifyTestResourcesTaskSelection'

        then:
        result.task(':verifyTestResourcesTaskSelection').outcome == TaskOutcome.SUCCESS
    }

    def "runs the application with test resources support"() {
        withSample("test-resources/data-mysql")

        when:
        def result = build '-DinterruptStartup=true', 'run'

        then:
        result.task(':run').outcome == TaskOutcome.SUCCESS
        result.output.contains "Loaded 2 test resources resolvers"
        result.output.contains "io.micronaut.testresources.mysql.MySQLTestResourceProvider"
        result.output.contains "io.micronaut.testresources.testcontainers.GenericTestContainerProvider"
    }

    def "standalone test resources service survives after build-owned service shutdown"() {
        withSample("test-resources/data-mysql")
        buildFile.text = buildFile.text
                .replace('implementation("io.micronaut:micronaut-jackson-databind")', 'implementation("io.micronaut.serde:micronaut-serde-jackson")')
                .replace('runtimeOnly("io.micronaut:micronaut-jackson-databind")', '')

        when:
        def firstRun = build '-DinterruptStartup=true', 'run'

        then:
        firstRun.task(':run').outcome == TaskOutcome.SUCCESS

        when:
        def firstRunPort = testResourcesPort()

        then:
        eventuallyCannotConnectTo(firstRunPort)

        when:
        def startResult = build 'startTestResourcesService'
        def standalonePort = testResourcesPort()

        and:
        def secondRun = build '-DinterruptStartup=true', 'run'
        def reusedPort = testResourcesPort()

        and:
        def stopResult = build 'stopTestResourcesService'

        then:
        startResult.task(':internalStartTestResourcesService').outcome == TaskOutcome.SUCCESS
        secondRun.task(':run').outcome == TaskOutcome.SUCCESS
        standalonePort == reusedPort
        stopResult.task(':stopTestResourcesService').outcome == TaskOutcome.SUCCESS

        cleanup:
        try {
            build 'stopTestResourcesService'
        } catch (ignored) {
            // best effort cleanup when the assertion path already stopped the service
        }
    }

    private int testResourcesPort() {
        file(".micronaut/test-resources/test-resources-port.txt").text.trim() as int
    }

    private boolean canConnectTo(int port) {
        try {
            new Socket().withCloseable {
                it.connect(new InetSocketAddress("127.0.0.1", port), 1000)
            }
            return true
        } catch (IOException ignored) {
            return false
        }
    }

    private boolean eventuallyCannotConnectTo(int port) {
        long deadline = System.currentTimeMillis() + 5000
        while (canConnectTo(port)) {
            if (System.currentTimeMillis() >= deadline) {
                return false
            }
            Thread.sleep(100)
        }
        true
    }

    private void withTestResourcesConfiguration(String configuration) {
        updateBuildFile("TEST_RESOURCES_MARKER", configuration)
    }

    private void withDependencies(String dependencies) {
        updateBuildFile("TEST_DEPENDENCIES_MARKER", dependencies)
    }

    private void withJvmTestSuite() {
        buildFile << '''

testing {
    suites {
        integrationTest(org.gradle.api.plugins.jvm.JvmTestSuite) {
            useJUnitJupiter()
            dependencies {
                implementation project()
            }
        }
    }
}
'''
    }

    private void withTaskSelectionVerification(boolean testTaskEnabled, boolean integrationTestEnabled) {
        buildFile << """

def assertTestResourcesTaskConfiguration = { org.gradle.api.tasks.testing.Test task, boolean expected ->
    def dependencyPaths = task.taskDependencies.getDependencies(task)*.path as Set
    def hasStartDependency = dependencyPaths.contains(":internalStartTestResourcesService")
    def hasServerConnectionProvider = task.jvmArgumentProviders.any {
        it.class.name == "io.micronaut.gradle.testresources.ServerConnectionParametersProvider"
    }
    assert hasStartDependency == expected : "Expected \$task.name start dependency to be \$expected but was \$hasStartDependency"
    assert hasServerConnectionProvider == expected : "Expected \$task.name server connection provider to be \$expected but was \$hasServerConnectionProvider"
}

tasks.register("verifyTestResourcesTaskSelection") {
    doLast {
        assertTestResourcesTaskConfiguration(tasks.named("test").get(), ${testTaskEnabled})
        assertTestResourcesTaskConfiguration(tasks.named("integrationTest").get(), ${integrationTestEnabled})
    }
}
"""
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
