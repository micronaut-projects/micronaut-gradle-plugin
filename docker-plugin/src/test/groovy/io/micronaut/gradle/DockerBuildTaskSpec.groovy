package io.micronaut.gradle

import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf
import spock.lang.Issue

@IgnoreIf({ os.windows })
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
                version "$micronautVersion"
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

    def "test build docker image using custom Dockerfile"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.docker"
            }
            
            micronaut {
                version "$micronautVersion"
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

        testProjectDir.newFile("Dockerfile") << """
FROM eclipse-temurin:17-jre-focal
WORKDIR /home/alternate
COPY layers/libs /home/alternate/libs
COPY layers/app/application.jar /home/alternate/application.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/home/alternate/application.jar"]
"""
        when:
        def result = build('dockerBuild', '-s')

        def task = result.task(":dockerBuild")
        then:
        result.output.contains("WORKDIR /home/alternate")
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
                version "$micronautVersion"
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
                version "$micronautVersion"
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
        dockerfile == """FROM eclipse-temurin:17-jre-focal
WORKDIR /home/alternate
COPY layers/libs /home/alternate/libs
COPY layers/snapshot_libs /home/alternate/libs
COPY layers/project_libs /home/alternate/libs
COPY layers/app /home/alternate/
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/home/alternate/application.jar"]
"""
    }

    def "can use the COPY --link option"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """import io.micronaut.gradle.docker.MicronautDockerfile

            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.docker"
            }
            
            micronaut {
                version "$micronautVersion"
            }
            
            $repositoriesBlock

            mainClassName="example.Application"

            tasks.withType(MicronautDockerfile).configureEach {
                useCopyLink = true
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
        dockerfile == """FROM eclipse-temurin:17-jre-focal
WORKDIR /home/app
COPY --link layers/libs /home/app/libs
COPY --link layers/snapshot_libs /home/app/libs
COPY --link layers/project_libs /home/app/libs
COPY --link layers/app /home/app/
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/home/app/application.jar"]
"""
    }

}
