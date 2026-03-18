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

    def "can generate an java OpenAPI client implementation (APIs only)"() {
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
                        generateModels = false
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
        result.task(":generateClientOpenApiModels").outcome == TaskOutcome.SKIPPED
        result.task(":compileJava").outcome == TaskOutcome.FAILED

        and:
        !file("build/generated/openapi/generateClientOpenApiModels/src/main/java/io/micronaut/openapi/model/Pet.java").exists()

    }

    def "can generate an java OpenAPI client implementation (models only)"() {
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
                        generateApis = false
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
        result.task(":generateClientOpenApiApis").outcome == TaskOutcome.SKIPPED
        result.task(":generateClientOpenApiModels").outcome == TaskOutcome.SUCCESS
        result.task(":compileJava").outcome == TaskOutcome.SUCCESS

        and:
        file("build/generated/openapi/generateClientOpenApiModels/src/main/java/io/micronaut/openapi/model/Pet.java").exists()

    }

    def "can generate an java OpenAPI client implementation with clientId"() {
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
                        clientId = "my-client"
                        implicitHeadersRegex = ".*"
                        useEnumCaseInsensitive = true
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
        def petApiFile = file("build/generated/openapi/generateClientOpenApiApis/src/main/java/io/micronaut/openapi/api/PetApi.java")
        petApiFile.exists()
        petApiFile.readLines()
                .findAll { it.contains('@Client("my-client")') }
                .size() == 1
    }

    def "can generate an java OpenAPI client implementation with clientId and clientPath"() {
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
                        clientId = "my-client"
                        clientPath = true
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
        def petApiFile = file("build/generated/openapi/generateClientOpenApiApis/src/main/java/io/micronaut/openapi/api/PetApi.java")
        petApiFile.exists()
        petApiFile.readLines()
                .findAll { it.contains('@Client(id = "my-client", path = "${my-client.base-path}")') }
                .size() == 1
    }

    def "can generate an java OpenAPI client implementation without clientId"() {
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
        def petApiFile = file("build/generated/openapi/generateClientOpenApiApis/src/main/java/io/micronaut/openapi/api/PetApi.java")
        petApiFile.exists()
        petApiFile.readLines()
                .findAll { it.contains('@Client("${openapi-micronaut-client.base-path}")') }
                .size() == 1
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
                        useUrlConnectionCache = true
                        noArgsConstructor = true
                        useTags = false
                        generateOperationOnlyForFirstTag = false
                        useAuth = true
                        useOauth = false
                        useBasicAuth = true
                        useApiKeyAuth = true
                        generateAuthClasses = true
                        authFilter = true
                        authConfigName = "testAuth"
                        generateEnumConverters = false
                        authFilterClientIds = ["client1"]
                        authFilterExcludedClientIds = ["client2"]
                        authorizationFilterPattern = "/path1;/path2"
                        authorizationFilterPatternStyle = "REGEX"
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
                implementation "io.micronaut.security:micronaut-security-jwt"
                implementation "io.micronaut.security:micronaut-security-oauth2"
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
                    client(file("petstore.json")) {
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
            application { mainClass = "example.Application" }

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
        result.task(":generateClientOpenApiApis").outcome == TaskOutcome.SUCCESS
        result.task(":generateClientOpenApiModels").outcome == TaskOutcome.SUCCESS
        result.task(":compileJava").outcome == TaskOutcome.SUCCESS

        and:
        file("build/generated/openapi/generateClientOpenApiApis/src/main/java/io/micronaut/openapi/api/PetApi.java").exists()
        file("build/generated/openapi/generateClientOpenApiModels/src/main/java/io/micronaut/openapi/model/Pet.java").exists()
        file("build/classes/java/main/io/micronaut/openapi/api/PetApi.class").exists()
        file("build/classes/java/main/io/micronaut/openapi/model/Pet.class").exists()
    }
}
