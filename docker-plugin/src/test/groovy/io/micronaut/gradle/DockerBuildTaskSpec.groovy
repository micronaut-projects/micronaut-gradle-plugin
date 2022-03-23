package io.micronaut.gradle

import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Requires

@IgnoreIf({ os.windows })
@Requires({ jvm.isJava11Compatible() })
class DockerBuildTaskSpec extends AbstractGradleBuildSpec {

    def "test build docker image"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.docker"
            }
            
            micronaut {
                version "2.3.3"
            }
            
            $repositoriesBlock

            mainClassName="example.Application"
            
        """
        testProjectDir.newFolder("src", "main", "java", "example")
        def javaFile = testProjectDir.newFile("src/main/java/example/Application.java")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example;

class Application {
    public static void main(String... args) {
    
    }
}
"""

        when:
        def result = build('dockerBuild', '-s')

        def task = result.task(":dockerBuild")
        then:
        result.output.contains("Successfully tagged hello-world:latest")
        task.outcome == TaskOutcome.SUCCESS

    }

    @Issue('https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/161')
    void 'create group and user for running the application instead of using root'() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.docker"
            }

            micronaut {
                version "2.3.3"
            }

            $repositoriesBlock

            mainClassName="example.Application"

        """
        testProjectDir.newFolder("src", "main", "java", "example")
        def javaFile = testProjectDir.newFile("src/main/java/example/Application.java")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example;

class Application {
    public static void main(String... args) {

    }
}
"""

        when:
        def result = build('dockerfile', '-s')

        then:
        def dockerfileTask = result.task(":dockerfile")
        dockerfileTask.outcome == TaskOutcome.SUCCESS

        and:
        def dockerfile = new File(testProjectDir.root, 'build/docker/main/Dockerfile').readLines('UTF-8')
    }

    @Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/402")
    def "can override default working dir"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """import io.micronaut.gradle.docker.DockerBuildOptions

            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.docker"
            }
            
            micronaut {
                version "3.4.0"
            }
            
            $repositoriesBlock

            mainClassName="example.Application"

            tasks.withType(DockerBuildOptions).configureEach {
                targetWorkingDirectory = "/home/alternate"
            }
            
        """
        testProjectDir.newFolder("src", "main", "java", "example")
        def javaFile = testProjectDir.newFile("src/main/java/example/Application.java")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example;

class Application {
    public static void main(String... args) {
    
    }
}
"""

        when:
        def result = build('dockerfile', '-s')

        then:
        def dockerfileTask = result.task(":dockerfile")
        dockerfileTask.outcome == TaskOutcome.SUCCESS

        and:
        def dockerfile = new File(testProjectDir.root, 'build/docker/main/Dockerfile').text
        dockerfile == """FROM openjdk:17-alpine
WORKDIR /home/alternate
COPY layers/libs /home/alternate/libs
COPY layers/classes /home/alternate/classes
COPY layers/resources /home/alternate/resources
COPY layers/application.jar /home/alternate/application.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/home/alternate/application.jar"]
"""
    }

}
