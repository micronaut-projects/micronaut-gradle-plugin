package io.micronaut.gradle.docker

import io.micronaut.gradle.fixtures.AbstractEagerConfiguringFunctionalTest
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue

import java.util.jar.JarFile

@Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/820")
class DockerJvmFunctionalTest extends AbstractEagerConfiguringFunctionalTest {

    void "assemble keeps explicit application main class for lambda_provided when dockerfile is customized"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.docker"
            }

            micronaut {
                version "$micronautVersion"
                runtime "lambda_provided"
            }

            $repositoriesBlock

            application {
                mainClass.set("example.Application")
            }

            dockerfile {
                baseImage("test_base_image_jvm")
            }
        """
        testProjectDir.newFolder("src", "main", "java", "example")
        def javaFile = testProjectDir.newFile("src/main/java/example/Application.java")
        javaFile << """
package example;

public class Application {
    public static void main(String... args) {
    }
}
"""

        when:
        def result = build("assemble")
        def runnerJar = new File(testProjectDir.root, "build/libs/hello-world-runner.jar")
        def manifestMainClass
        new JarFile(runnerJar).withCloseable { jar ->
            manifestMainClass = jar.manifest.mainAttributes.getValue("Main-Class")
        }

        then:
        result.task(":assemble").outcome == TaskOutcome.SUCCESS
        manifestMainClass == "example.Application"
    }
}
