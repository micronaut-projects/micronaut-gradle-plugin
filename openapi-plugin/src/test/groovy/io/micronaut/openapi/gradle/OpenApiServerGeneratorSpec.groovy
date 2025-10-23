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

    def "can generate an java OpenAPI server (API only)"() {
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
                        generateModels = false
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
        result.task(":generateServerOpenApiModels").outcome == TaskOutcome.SKIPPED
        result.task(":compileJava").outcome == TaskOutcome.FAILED

        and:
        file("build/generated/openapi/generateServerOpenApiApis/src/main/java/io/micronaut/openapi/api/PetApi.java").exists()
        !file("build/generated/openapi/generateServerOpenApiModels/src/main/java/io/micronaut/openapi/model/Pet.java").exists()
        !file("build/classes/java/main/io/micronaut/openapi/model/Pet.class").exists()
    }

    def "can generate an java OpenAPI server (models only)"() {
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
                        generateApis = false
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
        result.task(":generateServerOpenApiApis").outcome == TaskOutcome.SKIPPED
        result.task(":generateServerOpenApiModels").outcome == TaskOutcome.SUCCESS
        result.task(":compileJava").outcome == TaskOutcome.SUCCESS

        and:
        !file("build/generated/openapi/generateServerOpenApiApis/src/main/java/io/micronaut/openapi/api/PetApi.java").exists()
        file("build/generated/openapi/generateServerOpenApiModels/src/main/java/io/micronaut/openapi/model/Pet.java").exists()
        !file("build/classes/java/main/io/micronaut/openapi/api/PetApi.class").exists()
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
                        useAuth = true
                        userParameterMode = "AUTHENTICATION"
                        aot = true
                        apiNamePrefix = "ApiPrefix"
                        apiNameSuffix = "ApiSuffix"
                        modelNamePrefix = "ModelPrefix"
                        modelNameSuffix = "ModelSuffix"
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
        file("build/generated/openapi/generateServerOpenApiApis/src/main/java/io/micronaut/openapi/api/ApiPrefixPetApiSuffix.java").exists()
        file("build/generated/openapi/generateServerOpenApiModels/src/main/java/io/micronaut/openapi/model/ModelPrefixPetModelSuffix.java").exists()
        file("build/classes/java/main/io/micronaut/openapi/api/ApiPrefixPetApiSuffix.class").exists()
        file("build/classes/java/main/io/micronaut/openapi/model/ModelPrefixPetModelSuffix.class").exists()
    }

    def "can generate an java OpenAPI server implementation with custom name and local file"() {
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
                    server("myServer", file("petstore.json")) {
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
        result.task(":generateMyServerOpenApiApis").outcome == TaskOutcome.SUCCESS
        result.task(":generateMyServerOpenApiModels").outcome == TaskOutcome.SUCCESS
        result.task(":compileJava").outcome == TaskOutcome.SUCCESS

        and:
        file("build/generated/openapi/generateMyServerOpenApiApis/src/main/java/io/micronaut/openapi/api/PetApi.java").exists()
        file("build/generated/openapi/generateMyServerOpenApiModels/src/main/java/io/micronaut/openapi/model/Pet.java").exists()
        file("build/classes/java/main/io/micronaut/openapi/api/PetApi.class").exists()
        file("build/classes/java/main/io/micronaut/openapi/model/Pet.class").exists()
    }

    def "check properties for micronaut-openapi 6.14.0"() {
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
                    server("myServer", file("petstore.json")) {
                        lang = "java"
                        useReactive = false
                        useSealed = true
                        jsonIncludeAlwaysForRequiredFields = true
                        requiredPropertiesInConstructor = false
                        generateControllerAsAbstract = true
                    }
                }
            }
            
            $repositoriesBlock
            mainClassName="example.Application"

            dependencies {

                annotationProcessor "io.micronaut.serde:micronaut-serde-processor"

                implementation "io.micronaut.security:micronaut-security"
                implementation "io.micronaut.serde:micronaut-serde-jackson"
            }
        """

        withPetstore()

        when:
        def result = build('test')

        then:
        result.task(":generateMyServerOpenApiApis").outcome == TaskOutcome.SUCCESS
        result.task(":generateMyServerOpenApiModels").outcome == TaskOutcome.SUCCESS
        result.task(":compileJava").outcome == TaskOutcome.SUCCESS

        and:
        file("build/generated/openapi/generateMyServerOpenApiApis/src/main/java/io/micronaut/openapi/api/PetApi.java").exists()
        file("build/generated/openapi/generateMyServerOpenApiModels/src/main/java/io/micronaut/openapi/model/Pet.java").exists()
        file("build/classes/java/main/io/micronaut/openapi/api/PetApi.class").exists()
        file("build/classes/java/main/io/micronaut/openapi/model/Pet.class").exists()
    }
}
