package io.micronaut.gradle.openapi


import org.gradle.testkit.runner.TaskOutcome

class OpenApiServerWithKotlinSpec extends AbstractOpenApiWithKotlinSpec {

    def "can generate an kotlin OpenAPI server implementation with properties (KAPT)"() {
        given:
        settingsFile << "rootProject.name = 'openapi-server'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.openapi"
                id "org.jetbrains.kotlin.jvm" version "$kotlinVersion"
                id "org.jetbrains.kotlin.plugin.allopen" version "$kotlinVersion"
                id "org.jetbrains.kotlin.kapt" version "$kotlinVersion"
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
                        nameMapping = [test: "changedTest"]
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
        result.task(":compileKotlin").outcome == TaskOutcome.SUCCESS

        and:
        file("build/generated/openapi/generateServerOpenApiApis/src/main/kotlin/io/micronaut/openapi/api/PetApi.kt").exists()
        file("build/generated/openapi/generateServerOpenApiModels/src/main/kotlin/io/micronaut/openapi/model/Pet.kt").exists()
        file("build/classes/kotlin/main/io/micronaut/openapi/api/PetApi.class").exists()
        file("build/classes/kotlin/main/io/micronaut/openapi/model/Pet.class").exists()
    }

    def "can generate an kotlin OpenAPI server implementation with properties (KSP)"() {
        given:
        settingsFile << "rootProject.name = 'openapi-server'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.openapi"
                id "org.jetbrains.kotlin.jvm" version "$kotlinVersion"
                id "org.jetbrains.kotlin.plugin.allopen" version "$kotlinVersion"
                id "com.google.devtools.ksp" version "$kspVersion"
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
                        ksp = true
                        nameMapping = [test: "changedTest"]
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
        result.task(":compileKotlin").outcome == TaskOutcome.SUCCESS

        and:
        file("build/generated/openapi/generateServerOpenApiApis/src/main/kotlin/io/micronaut/openapi/api/PetApi.kt").exists()
        file("build/generated/openapi/generateServerOpenApiModels/src/main/kotlin/io/micronaut/openapi/model/Pet.kt").exists()
        file("build/classes/kotlin/main/io/micronaut/openapi/api/PetApi.class").exists()
        file("build/classes/kotlin/main/io/micronaut/openapi/model/Pet.class").exists()
    }
}
