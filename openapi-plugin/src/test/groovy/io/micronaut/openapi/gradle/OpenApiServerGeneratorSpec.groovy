package io.micronaut.openapi.gradle

import org.gradle.testkit.runner.TaskOutcome

class OpenApiServerGeneratorSpec extends AbstractOpenApiGeneratorSpec {

    def "can generate an java OpenAPI server implementation"() {
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

    def "can generate an java OpenAPI server implementation with properties"() {
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
                        lang = "java"
                        lombok = true
                        useReactive = true
                        generatedAnnotation = false
                        fluxForArrays = true
                        aot = true
                    }
                }
            }
            
            $repositoriesBlock
            mainClassName="example.Application"

            dependencies {

                annotationProcessor "org.projectlombok:lombok"
                annotationProcessor "io.micronaut.serde:micronaut-serde-processor"

                compileOnly "org.projectlombok:lombok"

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

    def "can generate an kotlin OpenAPI server implementation with properties (KAPT)"() {
        given:
        settingsFile << "rootProject.name = 'openapi-server'"
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
                runtime "netty"
                testRuntime "junit5"
                openapi {
                    server(file("petstore.json")) {
                        lang = "kotlin"
                        useReactive = true
                        generatedAnnotation = false
                        fluxForArrays = true
                        aot = true
                    }
                }
            }
            
            $repositoriesBlock
            mainClassName="example.Application"

            dependencies {

                kapt "io.micronaut.serde:micronaut-serde-processor"

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

    def "can generate an kotlin OpenAPI server implementation with properties (KSP)"() {
        given:
        settingsFile << "rootProject.name = 'openapi-server'"
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
                runtime "netty"
                testRuntime "junit5"
                openapi {
                    server(file("petstore.json")) {
                        lang = "kotlin"
                        useReactive = true
                        generatedAnnotation = false
                        fluxForArrays = true
                        aot = true
                    }
                }
            }
            
            $repositoriesBlock
            mainClassName="example.Application"

            dependencies {

                ksp "io.micronaut.serde:micronaut-serde-processor"

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
