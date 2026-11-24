package io.micronaut.gradle


import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class AutomaticDependencySpec extends Specification {
    def "automatically adds annotation processor dependency for #group"() {
        def project = ProjectBuilder.builder().build()
        project.extensions.add('micronautVersion', 'any')
        project.pluginManager.apply(MicronautMinimalLibraryPlugin)

        when:
        project.configurations.implementation.dependencies.add(project.dependencies.create("$group:micronaut-any"))
        project.evaluate()

        // this variable is used to make sure other automatic dependencies are not used
        // when a particular dependency is found
        def shouldNotHave = "io.micronaut.validation:micronaut-validation-processor"
        if (group == "io.micronaut.validation") {
            shouldNotHave = "io.micronaut.data:micronaut-data-processor"
        }

        then:
        project.configurations.annotationProcessor.incoming.dependencies.find { "${it.group}:${it.name}" == processor }
        !project.configurations.annotationProcessor.incoming.dependencies.find { "${it.group}:${it.name}" == shouldNotHave }

        where:
        group                      | processor
        "io.micronaut.data"        | "io.micronaut.data:micronaut-data-processor"
        "io.micronaut.jaxrs"       | "io.micronaut.jaxrs:micronaut-jaxrs-processor"
        "io.micronaut.micrometer"  | "io.micronaut.micrometer:micronaut-micrometer-annotation"
        "io.micronaut.microstream" | "io.micronaut.microstream:micronaut-microstream-annotations"
        "io.micronaut.openapi"     | "io.micronaut.openapi:micronaut-openapi"
        "io.micronaut.security"    | "io.micronaut.security:micronaut-security-annotations"
        "io.micronaut.serde"       | "io.micronaut.serde:micronaut-serde-processor"
        "io.micronaut.spring"      | "io.micronaut.spring:micronaut-spring-annotation"
        "io.micronaut.tracing"     | "io.micronaut.tracing:micronaut-tracing-annotation"
        "io.micronaut.validation"  | "io.micronaut.validation:micronaut-validation-processor"
    }

    def "BOMs do not trigger processors in #scope (enforced: #enforced, library: #library)"() {
        given:
        def project = ProjectBuilder.builder().build()
        project.extensions.add('micronautVersion', 'any')
        project.pluginManager.apply(MicronautMinimalLibraryPlugin)
        def boms = project.configurations.create('globalBoms')
        project.configurations.getByName(scope).extendsFrom(boms)
        def coordinates = 'io.micronaut.validation:micronaut-validation-bom:5.2.1'
        def bom = enforced ? project.dependencies.enforcedPlatform(coordinates) : project.dependencies.platform(coordinates)
        boms.dependencies.add(bom)
        if (library) {
            project.dependencies.add(scope, 'io.micronaut.validation:micronaut-validation')
        }

        when:
        project.evaluate()

        then:
        def processors = project.configurations.annotationProcessor.incoming.dependencies
        processors.any { it.group == 'io.micronaut.validation' && it.name == 'micronaut-validation-processor' } == library
        boms.dependencies.contains(bom)

        where:
        [scope, enforced, library] << [['implementation', 'compileOnly'], [false, true], [false, true]].combinations()
    }

}
