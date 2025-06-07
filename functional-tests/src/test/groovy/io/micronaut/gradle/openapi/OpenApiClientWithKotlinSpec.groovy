package io.micronaut.gradle.openapi


import org.gradle.testkit.runner.TaskOutcome

class OpenApiClientWithKotlinSpec extends AbstractOpenApiWithKotlinSpec {

    def "can generate an kotlin OpenAPI client implementation with clientId (KAPT)"() {
        given:
        settingsFile << "rootProject.name = 'openapi-client'"
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
                openapi {
                    client(file("petstore.json")) {
                        lang = "kotlin"
                        useReactive = true
                        generatedAnnotation = false
                        fluxForArrays = true
                        nameMapping = [test: "changedTest"]
                        clientId = "my-client"
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
        result.task(":compileKotlin").outcome == TaskOutcome.SUCCESS

        and:
        file("build/generated/openapi/generateClientOpenApiModels/src/main/kotlin/io/micronaut/openapi/model/Pet.kt").exists()
        def petApiFile = file("build/generated/openapi/generateClientOpenApiApis/src/main/kotlin/io/micronaut/openapi/api/PetApi.kt")
        petApiFile.exists()
        petApiFile.readLines()
                .findAll { it.contains('@Client("my-client")') }
                .size() == 1
    }

    def "can generate an kotlin OpenAPI client implementation with clientId and clientPath (KAPT)"() {
        given:
        settingsFile << "rootProject.name = 'openapi-client'"
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
                openapi {
                    client(file("petstore.json")) {
                        lang = "kotlin"
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
        result.task(":compileKotlin").outcome == TaskOutcome.SUCCESS

        and:
        file("build/generated/openapi/generateClientOpenApiModels/src/main/kotlin/io/micronaut/openapi/model/Pet.kt").exists()
        def petApiFile = file("build/generated/openapi/generateClientOpenApiApis/src/main/kotlin/io/micronaut/openapi/api/PetApi.kt")
        petApiFile.exists()
        petApiFile.readLines()
                .findAll { it.contains('@Client(id = "my-client", path = "\\${my-client.base-path}")') }
                .size() == 1
    }

    def "can generate an kotlin OpenAPI client implementation without clientId (KAPT)"() {
        given:
        settingsFile << "rootProject.name = 'openapi-client'"
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
                openapi {
                    client(file("petstore.json")) {
                        lang = "kotlin"
                        useReactive = true
                        generatedAnnotation = false
                        fluxForArrays = true
                        nameMapping = [test: "changedTest"]
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
        result.task(":compileKotlin").outcome == TaskOutcome.SUCCESS

        and:
        file("build/generated/openapi/generateClientOpenApiModels/src/main/kotlin/io/micronaut/openapi/model/Pet.kt").exists()
        def petApiFile = file("build/generated/openapi/generateClientOpenApiApis/src/main/kotlin/io/micronaut/openapi/api/PetApi.kt")
        petApiFile.exists()
        petApiFile.readLines()
                .findAll { it.contains('@Client("\\${openapi-micronaut-client.base-path}")') }
                .size() == 1
    }

    def "can generate an kotlin OpenAPI client implementation with clientId (KSP)"() {
        given:
        settingsFile << "rootProject.name = 'openapi-client'"
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
                openapi {
                    client(file("petstore.json")) {
                        lang = "kotlin"
                        useReactive = true
                        generatedAnnotation = false
                        fluxForArrays = true
                        ksp = true
                        nameMapping = [test: "changedTest"]
                        clientId = "my-client"
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
        result.task(":compileKotlin").outcome == TaskOutcome.SUCCESS

        and:
        file("build/generated/openapi/generateClientOpenApiModels/src/main/kotlin/io/micronaut/openapi/model/Pet.kt").exists()
        def petApiFile = file("build/generated/openapi/generateClientOpenApiApis/src/main/kotlin/io/micronaut/openapi/api/PetApi.kt")
        petApiFile.exists()
        petApiFile.readLines()
                .findAll { it.contains('@Client("my-client")') }
                .size() == 1
    }

    def "can generate an kotlin OpenAPI client implementation with clientId and clientPath (KSP)"() {
        given:
        settingsFile << "rootProject.name = 'openapi-client'"
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
                openapi {
                    client(file("petstore.json")) {
                        lang = "kotlin"
                        useReactive = true
                        generatedAnnotation = false
                        fluxForArrays = true
                        ksp = true
                        nameMapping = [test: "changedTest"]
                        clientId = "my-client"
                        clientPath = true
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
        result.task(":compileKotlin").outcome == TaskOutcome.SUCCESS

        and:
        file("build/generated/openapi/generateClientOpenApiModels/src/main/kotlin/io/micronaut/openapi/model/Pet.kt").exists()
        def petApiFile = file("build/generated/openapi/generateClientOpenApiApis/src/main/kotlin/io/micronaut/openapi/api/PetApi.kt")
        petApiFile.exists()
        petApiFile.readLines()
                .findAll { it.contains('@Client(id = "my-client", path = "\\${my-client.base-path}")') }
                .size() == 1
    }

    def "can generate an kotlin OpenAPI client implementation without clientId (KSP)"() {
        given:
        settingsFile << "rootProject.name = 'openapi-client'"
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
                openapi {
                    client(file("petstore.json")) {
                        lang = "kotlin"
                        useReactive = true
                        generatedAnnotation = false
                        fluxForArrays = true
                        ksp = true
                        nameMapping = [test: "changedTest"]
                        jvmOverloads = true
                        jvmRecord = false
                        javaCompatibility = false
                        useUrlConnectionCache = true
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

                ksp "io.micronaut.serde:micronaut-serde-processor"
                ksp "io.micronaut.openapi:micronaut-openapi"
                ksp "io.micronaut.security:micronaut-security-annotations"

                implementation "io.micronaut.serde:micronaut-serde-jackson"
                implementation "io.micronaut.reactor:micronaut-reactor"
                implementation "io.micronaut:micronaut-inject-kotlin"
                implementation "io.micronaut.security:micronaut-security-jwt"
                implementation "io.micronaut.security:micronaut-security-oauth2"
            }

        """

        withPetstore()

        when:
        def result = build('test')

        then:
        result.task(":generateClientOpenApiApis").outcome == TaskOutcome.SUCCESS
        result.task(":generateClientOpenApiModels").outcome == TaskOutcome.SUCCESS
        result.task(":compileKotlin").outcome == TaskOutcome.SUCCESS

        and:
        file("build/generated/openapi/generateClientOpenApiModels/src/main/kotlin/io/micronaut/openapi/model/Pet.kt").exists()
        def petApiFile = file("build/generated/openapi/generateClientOpenApiApis/src/main/kotlin/io/micronaut/openapi/api/PetApi.kt")
        petApiFile.exists()
        petApiFile.readLines()
                .findAll { it.contains('@Client("\\${openapi-micronaut-client.base-path}")') }
                .size() == 1
    }

    def "check properties for micronaut-openapi 6.14.0"() {
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
                    client(file("petstore.json")) {
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
        result.task(":generateClientOpenApiApis").outcome == TaskOutcome.SUCCESS
        result.task(":generateClientOpenApiModels").outcome == TaskOutcome.SUCCESS
        result.task(":compileKotlin").outcome == TaskOutcome.SUCCESS

        and:
        file("build/classes/kotlin/main/io/micronaut/openapi/api/PetApi.class").exists()
        file("build/classes/kotlin/main/io/micronaut/openapi/model/Pet.class").exists()

        def petFile = file("build/generated/openapi/generateClientOpenApiModels/src/main/kotlin/io/micronaut/openapi/model/Pet.kt")
        petFile.exists()
        def petFileBody = new String(petFile.readBytes())
        petFileBody.contains("""
            @field:NotNull
            @field:JsonProperty(JSON_PROPERTY_NAME)
            @field:JsonInclude(JsonInclude.Include.ALWAYS)
            var name: String,
        """.stripIndent())

        def petApiFile = file("build/generated/openapi/generateClientOpenApiApis/src/main/kotlin/io/micronaut/openapi/api/PetApi.kt")
        petApiFile.exists()
        def petApiFileBody = new String(petApiFile.readBytes())
        petApiFileBody.contains("suspend fun updatePet(")
    }
}
