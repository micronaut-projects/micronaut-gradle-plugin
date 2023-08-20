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

    def "can generate an kotlin OpenAPI client implementation with some properties (KAPT)"() {
        given:
        settingsFile << "rootProject.name = 'openapi-client'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.openapi"
                id "org.jetbrains.kotlin.jvm" version "1.9.20"
                id "org.jetbrains.kotlin.plugin.allopen" version "1.9.20"
                id "org.jetbrains.kotlin.kapt" version "1.9.20"
            }
            
            micronaut {
                version "$micronautVersion"
                openapi {
                    client(file("petstore.json")) {
                        lang = "kotlin"
                        useReactive = true
                        generatedAnnotation = false
                        fluxForArrays = true
                    }
                }
            }
            
            $repositoriesBlock

            dependencies {

                kapt "io.micronaut.serde:micronaut-serde-processor"

                implementation "io.micronaut.serde:micronaut-serde-jackson"
                implementation "io.micronaut.reactor:micronaut-reactor"
                implementation "io.micronaut:micronaut-inject-kotlin"
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
        file("build/generated/openapi/generateClientOpenApiModels/src/main/kotlin/io/micronaut/openapi/model/Pet.kt").exists()
    }

    def "can generate an kotlin OpenAPI client implementation with some properties (KSP)"() {
        given:
        settingsFile << "rootProject.name = 'openapi-client'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.openapi"
                id "org.jetbrains.kotlin.jvm" version "1.9.20"
                id "org.jetbrains.kotlin.plugin.allopen" version "1.9.20"
                id "com.google.devtools.ksp" version "1.9.20-1.0.14"
            }
            
            micronaut {
                version "$micronautVersion"
                openapi {
                    client(file("petstore.json")) {
                        lang = "kotlin"
                        useReactive = true
                        generatedAnnotation = false
                        fluxForArrays = true
                    }
                }
            }
            
            $repositoriesBlock

            dependencies {

                ksp "io.micronaut.serde:micronaut-serde-processor"

                implementation "io.micronaut.serde:micronaut-serde-jackson"
                implementation "io.micronaut.reactor:micronaut-reactor"
                implementation "io.micronaut:micronaut-inject-kotlin"
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
        file("build/generated/openapi/generateClientOpenApiModels/src/main/kotlin/io/micronaut/openapi/model/Pet.kt").exists()
    }
}
