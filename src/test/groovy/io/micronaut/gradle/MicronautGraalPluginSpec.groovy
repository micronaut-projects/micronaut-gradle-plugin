package io.micronaut.gradle

import groovy.json.JsonSlurper
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class MicronautGraalPluginSpec extends Specification {

    @Rule TemporaryFolder testProjectDir = new TemporaryFolder()

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.newFile('settings.gradle')
        buildFile = testProjectDir.newFile('build.gradle')
    }

    void 'generate GraalVM resource-config.json with OpenAPI and resources included'() {
        given:
        testProjectDir.newFile('openapi.properties') << 'swagger-ui.enabled=true'
        testProjectDir.newFolder('src', 'main', 'resources')
        testProjectDir.newFile('src/main/resources/application.yml') << 'micronaut.application.name: hello-world'
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.application"
                id "io.micronaut.graalvm"
            }
            dependencies {
                annotationProcessor("io.micronaut.openapi:micronaut-openapi")
                implementation("io.swagger.core.v3:swagger-annotations")
            }
            micronaut {
                version "2.5.4"
            }
            repositories {
                mavenCentral()
            }
            group = "example.micronaut"
            mainClassName="example.Application"
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

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments('classes', '-i', '--stacktrace')
            .withPluginClasspath()
            .build()

        then:
        def task = result.task(":classes")
        task.outcome == TaskOutcome.SUCCESS

        and:
        def resourceConfigFile = new File(testProjectDir.root, 'build/classes/java/main/META-INF/native-image/example.micronaut/hello-world/resource-config.json')
        def resourceConfigJson = new JsonSlurper().parse(resourceConfigFile)

        resourceConfigJson.resources.pattern.any { it == "\\Qapplication.yml\\E" }
        resourceConfigJson.resources.pattern.any { it == "\\QMETA-INF/swagger/app-0.0.yml\\E" }
        resourceConfigJson.resources.pattern.any { it == "\\QMETA-INF/swagger/views/swagger-ui/index.html\\E" }
    }
}
