package io.micronaut.openapi.gradle

import org.gradle.testkit.runner.TaskOutcome

class OpenApiClientGeneratorSpec extends AbstractOpenApiGeneratorSpec {

    def "can generate an java OpenAPI client implementation"() {
        given:
        settingsFile << "rootProject.name = 'openapi-client'"
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

                annotationProcessor "io.micronaut.serde:micronaut-serde-processor"

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
        file("build/generated/openapi/generateClientOpenApiModels/src/main/java/io/micronaut/openapi/model/Pet.java").exists()

    }

    def "can generate an java OpenAPI client implementation with some properties"() {
        given:
        settingsFile << "rootProject.name = 'openapi-client'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.openapi"
            }
            
            micronaut {
                version "$micronautVersion"
                openapi {
                    client(file("petstore.json")) {
                        lang = "java"
                        lombok = true
                        useReactive = true
                        generatedAnnotation = false
                        fluxForArrays = true
                        nameMapping = [test: "changedTest"]
                    }
                }
            }
            
            $repositoriesBlock

            dependencies {

                annotationProcessor "org.projectlombok:lombok"
                annotationProcessor "io.micronaut.serde:micronaut-serde-processor"

                compileOnly "org.projectlombok:lombok"

                implementation "io.micronaut.serde:micronaut-serde-jackson"
                implementation "io.micronaut.reactor:micronaut-reactor"
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
        file("build/generated/openapi/generateClientOpenApiModels/src/main/java/io/micronaut/openapi/model/Pet.java").exists()
    }

    def "can generate an java OpenAPI client implementation using local file and custom name"() {
        given:
        settingsFile << "rootProject.name = 'openapi-client'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.openapi"
            }
            
            micronaut {
                version "$micronautVersion"
                openapi {
                    client("myClient", file("petstore.json")) {
                        lang = "java"
                        lombok = true
                        useReactive = true
                        generatedAnnotation = false
                        fluxForArrays = true
                    }
                }
            }
            
            $repositoriesBlock

            dependencies {

                annotationProcessor "org.projectlombok:lombok"
                annotationProcessor "io.micronaut.serde:micronaut-serde-processor"

                compileOnly "org.projectlombok:lombok"

                implementation "io.micronaut.serde:micronaut-serde-jackson"
                implementation "io.micronaut.reactor:micronaut-reactor"
            }

        """

        withPetstore()

        when:
        def result = build('test')

        then:
        result.task(":generateMyClientOpenApiApis").outcome == TaskOutcome.SUCCESS
        result.task(":generateMyClientOpenApiModels").outcome == TaskOutcome.SUCCESS
        result.task(":compileJava").outcome == TaskOutcome.SUCCESS

        and:
        file("build/generated/openapi/generateMyClientOpenApiModels/src/main/java/io/micronaut/openapi/model/Pet.java").exists()
    }
}
