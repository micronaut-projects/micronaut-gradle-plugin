package io.micronaut.gradle.jsonschema

import io.micronaut.gradle.AbstractGradleBuildSpec
import org.gradle.testkit.runner.TaskOutcome

abstract class JsonSchemaGeneratorSpec extends AbstractGradleBuildSpec{
    def "can generate an java source code from URL of schema"() {
        given:
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
                        
                    }
                }
            }
            
            $repositoriesBlock

            dependencies {

            }

        """

        when:
        def result = build('test')

        then:
        result.task(":generatingSourcesFromFhir").outcome == TaskOutcome.SUCCESS
        result.task(":compileJava").outcome == TaskOutcome.SUCCESS

        //and:
        //file("build/generated/openapi/generatingSourcesFromFhir/src/main/java/io/micronaut/openapi/model/Pet.java").exists()

    }

}
