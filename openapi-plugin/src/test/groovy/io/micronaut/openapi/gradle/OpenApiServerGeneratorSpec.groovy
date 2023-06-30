package io.micronaut.openapi.gradle


import org.gradle.testkit.runner.TaskOutcome

class OpenApiServerGeneratorSpec extends AbstractOpenApiGeneratorSpec {

    def "can generate an OpenAPI server implementation"() {
        given:
        settingsFile << "rootProject.name = 'openapi-server'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.openapi"
            }
            
            micronaut {
                version "$micronautVersion"
                runtime "netty"
                testRuntime "junit5"
                openapi {
                    server(file("petstore.json")) {
                        
                    }
                }
            }
            
            $repositoriesBlock
            mainClassName="example.Application"

            dependencies {
                implementation "io.micronaut.security:micronaut-security"
                implementation "io.micronaut.serde:micronaut-serde-jackson"
            }
        """

        withPetstore()

        when:
        def result = build('test')

        then:
        result.task(":generateServerOpenApiApis").outcome == TaskOutcome.SUCCESS
        result.task(":generateServerOpenApiModels").outcome == TaskOutcome.SUCCESS
        result.task(":compileJava").outcome == TaskOutcome.SUCCESS

        and:
        file("build/generated/openapi/generateServerOpenApiApis/src/main/java/io/micronaut/openapi/api/PetApi.java").exists()
        file("build/generated/openapi/generateServerOpenApiModels/src/main/java/io/micronaut/openapi/model/Pet.java").exists()
        file("build/classes/java/main/io/micronaut/openapi/api/PetApi.class").exists()
        file("build/classes/java/main/io/micronaut/openapi/model/Pet.class").exists()

    }

}
