package io.micronaut.gradle.testresources


import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf
import spock.lang.Issue

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

    @Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/815")
    def "doesn't fail if AOT plugin is applied with test resources and the library plugin"() {
        settingsFile << "rootProject.name = 'test'"
        buildFile << """plugins {
    id("io.micronaut.minimal.library")
    id("io.micronaut.aot")
    id("io.micronaut.test-resources")
}

micronaut {
    version "$micronautVersion"
}
"""

        when:
        def result = build 'help'

        then:
        !result.output.contains("Configuration with name 'optimizedRuntimeClasspath' not found.")
        result.task(':help').outcome == TaskOutcome.SUCCESS
    }

    @Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/852")
    def "test resources client shouldn't be on the AOT classpath"() {
        settingsFile << "rootProject.name = 'test'"
        buildFile << """plugins {
    id("io.micronaut.minimal.library")
    id("io.micronaut.aot")
    id("io.micronaut.test-resources")
}

repositories {
    mavenCentral()
}

micronaut {
    version "$micronautVersion"
}

tasks.named("prepareJitOptimizations") {
    doFirst {
        assert classpath.files.stream().noneMatch { it.name.startsWith("micronaut-test-resources-client-") }
    }
}

"""
        when:
        def result = build 'prepareJitOptimizations'

        then:
        result.task(":prepareJitOptimizations").outcome == TaskOutcome.SUCCESS
    }
}
