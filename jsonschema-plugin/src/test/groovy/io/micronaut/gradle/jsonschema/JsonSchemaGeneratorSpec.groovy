package io.micronaut.gradle.jsonschema

import io.micronaut.gradle.AbstractGradleBuildSpec
import org.gradle.testkit.runner.TaskOutcome

class JsonSchemaGeneratorSpec extends AbstractGradleBuildSpec{
    def "can generate an java source code from URL of schema"() {
        given:
        allowSnapshots = true
        settingsFile << "rootProject.name = 'jsonschema-url'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.jsonschema"
            }
            
            micronaut {
                version "$micronautVersion"
                jsonschema {
                    url("https://www.hl7.org/fhir/fhir.schema.json") {
                        lang = "java"
                    }
                }
            }
            
            $repositoriesBlock

            dependencies {

                annotationProcessor "io.micronaut.serde:micronaut-serde-processor"
                annotationProcessor "io.micronaut.validation:micronaut-validation-processor"

                implementation "io.micronaut.serde:micronaut-serde-jackson"
                implementation "io.micronaut.validation:micronaut-validation"
            }

        """


        when:
        def result = build('test')

        then:
        result.task(":generatingSourcesFromFhir").outcome == TaskOutcome.SUCCESS
        result.task(":compileJava").outcome == TaskOutcome.SUCCESS

        and:
        file("build/generated/jsonschema/generatingSourcesFromFhir/src/main/java/").exists()

    }

}
