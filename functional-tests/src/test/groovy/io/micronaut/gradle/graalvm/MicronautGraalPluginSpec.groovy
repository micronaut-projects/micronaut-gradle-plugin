package io.micronaut.gradle.graalvm

import groovy.json.JsonSlurper
import io.micronaut.gradle.AbstractGradleBuildSpec
import io.micronaut.gradle.fixtures.AbstractEagerConfiguringFunctionalTest
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Requires

@Requires({ AbstractGradleBuildSpec.graalVmAvailable && !os.windows })
class MicronautGraalPluginSpec extends AbstractEagerConfiguringFunctionalTest {

    void 'generate GraalVM resource-config.json with OpenAPI and resources included'() {
        given:
        withSwaggerMicronautApplication()

        when:
        def result = build('generateResourcesConfigFile', '-i', '--stacktrace')

        then:
        result.task(":classes").outcome == TaskOutcome.SUCCESS
        result.task(":generateResourcesConfigFile").outcome == TaskOutcome.SUCCESS

        and:
        def resourceConfigFile = new File(testProjectDir.root, 'build/native/generated/generateResourcesConfigFile/resource-config.json')
        def resourceConfigJson = new JsonSlurper().parse(resourceConfigFile)

        resourceConfigJson.resources.includes.pattern.any { it == "\\Qapplication.yml\\E" }
        resourceConfigJson.resources.includes.pattern.any { it == "\\QMETA-INF/swagger/app-0.0.yml\\E" }
        resourceConfigJson.resources.includes.pattern.any { it == "\\QMETA-INF/swagger/views/swagger-ui/index.html\\E" }

    }

    void 'generate GraalVM resource-config.json with OpenAPI and resources included without the Micronaut Application plugin'() {
        given:
        withSwaggerApplication()

        when:
        def result = build('generateResourcesConfigFile', '-i', '--stacktrace')

        then:
        result.task(":classes").outcome == TaskOutcome.SUCCESS
        result.task(":generateResourcesConfigFile").outcome == TaskOutcome.SUCCESS

        and:
        def resourceConfigFile = new File(testProjectDir.root, 'build/native/generated/generateResourcesConfigFile/resource-config.json')
        def resourceConfigJson = new JsonSlurper().parse(resourceConfigFile)

        resourceConfigJson.resources.includes.pattern.any { it == "\\Qapplication.yml\\E" }
        resourceConfigJson.resources.includes.pattern.any { it == "\\QMETA-INF/swagger/app-0.0.yml\\E" }
        resourceConfigJson.resources.includes.pattern.any { it == "\\QMETA-INF/swagger/views/swagger-ui/index.html\\E" }
    }

    @Requires({ AbstractGradleBuildSpec.graalVmAvailable && !os.windows })
    void 'native-image is called with the generated JSON file directory (Micronaut Application)'() {
        given:
        withSwaggerMicronautApplication()
        withNativeImageDryRun()

        when:
        def result = build('nativeCompile', '-i', '--stacktrace')

        then:
        result.task(":classes").outcome == TaskOutcome.SUCCESS
        result.task(":generateResourcesConfigFile").outcome == TaskOutcome.SUCCESS
        result.task(":nativeCompile").outcome == TaskOutcome.SUCCESS

        and:
        argFileContentsOf(result).contains("-H:ConfigurationFileDirectories=${new File(testProjectDir.root, 'build/native/generated/generateResourcesConfigFile').absolutePath}")

        where:
        plugins << [
                ['io.micronaut.application'],
                ['io.micronaut.minimal.application', 'io.micronaut.graalvm'],
        ]
    }

    void 'native-image is called with the generated JSON file directory (regular Application)'() {
        given:
        withSwaggerApplication()
        withNativeImageDryRun()

        when:
        def result = build('nativeCompile', '-i', '--stacktrace')

        then:
        result.task(":classes").outcome == TaskOutcome.SUCCESS
        result.task(":generateResourcesConfigFile").outcome == TaskOutcome.SUCCESS
        result.task(":nativeCompile").outcome == TaskOutcome.SUCCESS

        and:
        argFileContentsOf(result).contains("-H:ConfigurationFileDirectories=${new File(testProjectDir.root, 'build/native/generated/generateResourcesConfigFile').absolutePath}")
    }

    private void withSwaggerMicronautApplication() {
        testProjectDir.newFile('openapi.properties') << 'swagger-ui.enabled=true'
        testProjectDir.newFolder('src', 'main', 'resources')
        testProjectDir.newFile('src/main/resources/application.yml') << 'micronaut.application.name: hello-world'
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.graalvm"
            }
            dependencies {
                annotationProcessor("io.micronaut.openapi:micronaut-openapi")
                implementation("io.swagger.core.v3:swagger-annotations")
            }
            micronaut {
                version "$micronautVersion"
                runtime 'netty'
            }
            $repositoriesBlock
            group = "example.micronaut"
            mainClassName="example.Application"

            graalvmNative {
                binaries {
                    main {
                        useArgFile = true
                    }
                }
            }
        """
        testProjectDir.newFolder("src", "main", "java", "example")
        def javaFile = testProjectDir.newFile("src/main/java/example/Application.java")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

@OpenAPIDefinition(info = @Info(title = "app", version = "0.0"))
@io.micronaut.core.annotation.Introspected
class Application {
    public static void main(String... args) {
    }
}
"""
    }

    private void withSwaggerApplication() {
        testProjectDir.newFile('openapi.properties') << 'swagger-ui.enabled=true'
        testProjectDir.newFolder('src', 'main', 'resources')
        testProjectDir.newFile('src/main/resources/application.yml') << 'micronaut.application.name: hello-world'
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "application"
                id "io.micronaut.graalvm"
            }
            dependencies {
                annotationProcessor(enforcedPlatform("io.micronaut.platform:micronaut-platform:$micronautVersion"))
                annotationProcessor("io.micronaut:micronaut-inject-java")
                annotationProcessor("io.micronaut.openapi:micronaut-openapi")
                
                implementation(enforcedPlatform("io.micronaut.platform:micronaut-platform:$micronautVersion"))
                implementation("io.micronaut:micronaut-inject")
                implementation("io.micronaut:micronaut-http-server-netty")
                implementation("io.swagger.core.v3:swagger-annotations")
            }
            
            $repositoriesBlock
            group = "example.micronaut"
            mainClassName="example.Application"

            graalvmNative {
                binaries {
                    main {
                        useArgFile = true
                    }
                }
            }
        """
        testProjectDir.newFolder("src", "main", "java", "example")
        def javaFile = testProjectDir.newFile("src/main/java/example/Application.java")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

@OpenAPIDefinition(info = @Info(title = "app", version = "0.0"))
@io.micronaut.core.annotation.Introspected
class Application {
    public static void main(String... args) {
    }
}
"""
    }
}
