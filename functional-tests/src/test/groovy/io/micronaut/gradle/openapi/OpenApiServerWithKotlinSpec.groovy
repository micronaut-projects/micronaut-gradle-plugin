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
                id "org.jetbrains.kotlin.jvm" version "$kotlin2Version"
                id "org.jetbrains.kotlin.plugin.allopen" version "$kotlin2Version"
                id "org.jetbrains.kotlin.kapt" version "$kotlin2Version"
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
                        useAuth = true
                        userParameterMode = "AUTHENTICATION"
                        fluxForArrays = true
                        aot = true
                        nameMapping = [test: "changedTest"]
                    }
                }
            }
            
            $repositoriesBlock
            application { mainClass = "example.Application" }

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
                id "org.jetbrains.kotlin.jvm" version "$kotlin2Version"
                id "org.jetbrains.kotlin.plugin.allopen" version "$kotlin2Version"
                id "com.google.devtools.ksp" version "$ksp2Version"
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
                        useAuth = true
                        userParameterMode = "AUTHENTICATION"
                        fluxForArrays = true
                        aot = true
                        ksp = true
                        nameMapping = [test: "changedTest"]
                    }
                }
            }
            
            $repositoriesBlock
            application { mainClass = "example.Application" }

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

    def "check properties for micronaut-openapi 6.14.0"() {
        given:
        settingsFile << "rootProject.name = 'openapi-server'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.openapi"
                id "org.jetbrains.kotlin.jvm" version "$kotlin2Version"
                id "org.jetbrains.kotlin.plugin.allopen" version "$kotlin2Version"
                id "com.google.devtools.ksp" version "$ksp2Version"
            }
            
            micronaut {
                version "$micronautVersion"
                runtime "netty"
                testRuntime "junit5"
                openapi {
                    server(file("petstore.json")) {
                        lang = "kotlin"
                        useReactive = false
                        coroutines = true
                        jsonIncludeAlwaysForRequiredFields = true
                        requiredPropertiesInConstructor = false
                        generateControllerAsAbstract = true
                    }
                }
            }
            
            $repositoriesBlock
            application { mainClass = "example.Application" }

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
        file("build/classes/kotlin/main/io/micronaut/openapi/api/PetApi.class").exists()
        file("build/classes/kotlin/main/io/micronaut/openapi/model/Pet.class").exists()

        def petFile = file("build/generated/openapi/generateServerOpenApiModels/src/main/kotlin/io/micronaut/openapi/model/Pet.kt")
        petFile.exists()
        def petFileBody = new String(petFile.readBytes())
        petFileBody.contains("""
            @field:NotNull
            @field:JsonProperty(JSON_PROPERTY_NAME)
            @field:JsonInclude(JsonInclude.Include.ALWAYS)
            var name: String,
        """.stripIndent())

        def petApiFile = file("build/generated/openapi/generateServerOpenApiApis/src/main/kotlin/io/micronaut/openapi/api/PetApi.kt")
        petApiFile.exists()
        def petApiFileBody = new String(petApiFile.readBytes())
        !petApiFileBody.contains("@Controller")
        petApiFileBody.contains("suspend fun updatePet(")
    }
}
