package io.micronaut.gradle

import org.gradle.api.ProjectConfigurationException
import org.gradle.api.plugins.JavaPlugin
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class MicronautTestRuntimeSpec extends Specification {

    def "resolves and deduplicates configured test runtimes"() {
        given:
        def extension = newExtension()

        when:
        extension.testRuntime("junit5")
        extension.testRuntimes("junit5", "spock2", "none")

        then:
        extension.resolveTestRuntimes() == [MicronautTestRuntime.JUNIT_5, MicronautTestRuntime.SPOCK_2]
    }

    def "rejects incompatible runtime combinations from the same family"() {
        given:
        def project = ProjectBuilder.builder().build()
        project.extensions.add('micronautVersion', 'any')
        project.pluginManager.apply(MicronautMinimalLibraryPlugin)
        def extension = project.extensions.getByType(MicronautExtension)
        extension.testRuntimes(MicronautTestRuntime.KOTEST_4, MicronautTestRuntime.KOTEST_5)

        when:
        project.evaluate()

        then:
        def e = thrown(ProjectConfigurationException)
        e.cause instanceof IllegalArgumentException
        e.cause.message.contains("family 'kotest'")
    }

    def "collects dependency unions without duplicates per configuration"() {
        expect:
        MicronautTestRuntime.collectDependencies([MicronautTestRuntime.JUNIT_5, MicronautTestRuntime.SPOCK_2]) == [
            (JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME): [
                "org.junit.jupiter:junit-jupiter-api",
                "io.micronaut.test:micronaut-test-junit5",
                "org.spockframework:spock-core",
                "io.micronaut.test:micronaut-test-spock",
                "org.apache.groovy:groovy",
                "org.junit.platform:junit-platform-launcher"
            ],
            (JavaPlugin.TEST_RUNTIME_ONLY_CONFIGURATION_NAME): [
                "org.junit.jupiter:junit-jupiter-engine",
                "org.junit.platform:junit-platform-launcher"
            ],
            (JavaPlugin.TEST_COMPILE_ONLY_CONFIGURATION_NAME): [
                "io.micronaut:micronaut-inject-groovy"
            ]
        ]
    }

    private static MicronautExtension newExtension() {
        def project = ProjectBuilder.builder().build()
        project.objects.newInstance(MicronautExtension, new SourceSetConfigurerRegistry())
    }
}
