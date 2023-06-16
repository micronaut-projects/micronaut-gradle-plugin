package io.micronaut.openapi.gradle


import org.gradle.testkit.runner.TaskOutcome

class OpenApiClientGeneratorSpec extends AbstractOpenApiGeneratorSpec {

    def "can generate an OpenAPI client implementation"() {
        given:
        settingsFile << "rootProject.name = 'openapi-server'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.openapi"
            }
            
            micronaut {
                version "$micronautVersion"
                openapi {
                    client(file("petstore.json")) {
                        
                    }
                }
            }
            
            $repositoriesBlock

            dependencies {
                implementation "io.micronaut.serde:micronaut-serde-jackson"
            }

        """

        withPetstore()

        when:
        def result = build('test')

        then:
        result.task(":generateClientOpenApiApis").outcome == TaskOutcome.SUCCESS
        result.task(":generateClientOpenApiModels").outcome == TaskOutcome.SUCCESS
        result.task(":compileJava").outcome == TaskOutcome.SUCCESS

        and:
        file("build/generated/openapi/client/src/main/java/io/micronaut/openapi/model/Pet.java").exists()

    }

}
