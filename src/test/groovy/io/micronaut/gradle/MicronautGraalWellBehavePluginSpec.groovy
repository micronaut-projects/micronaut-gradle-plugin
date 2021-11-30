package io.micronaut.gradle

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class MicronautGraalWellBehavePluginSpec extends Specification {
    def "graal processor is added with plugin #plugin"() {
        def project = ProjectBuilder.builder().build()

        when:
        project.plugins.apply (plugin)

        then:
        project.configurations.annotationProcessor.dependencies.find { it.name == 'micronaut-graal' }

        where:
        plugin << [
                "io.micronaut.library",
                "io.micronaut.application",
        ]
    }
}
