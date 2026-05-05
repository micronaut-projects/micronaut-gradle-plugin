package io.micronaut.gradle.docker

import io.micronaut.gradle.fixtures.AbstractEagerConfiguringFunctionalTest
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue

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
        def startScript = new File(testProjectDir.root, "build/scripts/hello-world")

        then:
        result.task(":assemble").outcome == TaskOutcome.SUCCESS
        startScript.text.contains('example.Application')
        !startScript.text.contains('io.micronaut.function.aws.runtime.MicronautLambdaRuntime')
    }

    void "dockerfile uses lambda runtime entrypoint without changing application main class"() {
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
        def result = build("dockerfile", "assemble")
        def dockerFile = new File(testProjectDir.root, "build/docker/main/Dockerfile").readLines("UTF-8")
        def imageLibraries = new File(testProjectDir.root, "build/docker/main/layers/libs").list()
        def startScript = new File(testProjectDir.root, "build/scripts/hello-world")

        then:
        result.task(":dockerfile").outcome == TaskOutcome.SUCCESS
        result.task(":assemble").outcome == TaskOutcome.SUCCESS
        startScript.text.contains('example.Application')
        !startScript.text.contains('io.micronaut.function.aws.runtime.MicronautLambdaRuntime')
        dockerFile.find { s -> s == 'ENTRYPOINT ["java", "-cp", "/home/app/resources:/home/app/classes:/home/app/libs/*", "io.micronaut.function.aws.runtime.MicronautLambdaRuntime"]' }
        imageLibraries.any { it.startsWith("micronaut-function-aws-custom-runtime-") }
    }
}
