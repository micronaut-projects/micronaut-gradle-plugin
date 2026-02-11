package io.micronaut.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.MediaType
import spock.lang.IgnoreIf
import spock.lang.Issue

import static org.mockserver.model.HttpRequest.request
import static org.mockserver.model.HttpResponse.response

class DockerBuildTaskSpec extends AbstractGradleBuildSpec {
    private final String today = new Date().format("yyyyMMdd")
    private final String now = new Date().format("HHmmss")

    @IgnoreIf({ os.windows || !dockerAvailable })
    def "can apply the docker plugin first"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.docker"
                id "io.micronaut.minimal.application"
            }
            
            micronaut {
                version "$micronautVersion"
            }
            
            $repositoriesBlock

            application { mainClass = "example.Application" }
            
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


    @IgnoreIf({ os.windows || !dockerAvailable })
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

            application { mainClass = "example.Application" }
            
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

    @IgnoreIf({ os.windows || !dockerAvailable })
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

            application { mainClass = "example.Application" }
            
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
 FROM eclipse-temurin:25-jre
WORKDIR /home/alternate
COPY --link layers/libs /home/alternate/libs
COPY --link layers/app/application.jar /home/alternate/application.jar
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

            application { mainClass = "example.Application" }

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

    @IgnoreIf({ os.windows })
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

            application { mainClass = "example.Application" }

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
        dockerfile == """FROM eclipse-temurin:25-jre
WORKDIR /home/alternate
COPY --link layers/libs /home/alternate/libs
COPY --link layers/app /home/alternate/
COPY --link layers/resources /home/alternate/resources
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/home/alternate/application.jar"]
"""
    }

    @IgnoreIf({ os.windows })
    def "can disable the use of the COPY --link option"() {
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

            application { mainClass = "example.Application" }

            tasks.withType(MicronautDockerfile).configureEach {
                useCopyLink = false
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
        dockerfile == """FROM eclipse-temurin:25-jre
WORKDIR /home/app
COPY layers/libs /home/app/libs
COPY layers/app /home/app/
COPY layers/resources /home/app/resources
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/home/app/application.jar"]
"""
    }

    def "includes project dependencies in different layer"() {
        given:
        settingsFile << """rootProject.name = 'hello-world'
            include "project-lib"
        """
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.docker"
            }
            
            micronaut {
                version "$micronautVersion"
            }
            
            $repositoriesBlock

            application { mainClass = "example.Application" }

            dependencies {
                implementation project(":project-lib")
            }
            
        """
        file("project-lib").mkdir()
        file("project-lib/build.gradle") << """
            plugins {
                id "java-library"
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

        testProjectDir.newFolder("project-lib", "src", "main", "java", "example")
        def javaFile2 = testProjectDir.newFile("project-lib/src/main/java/example/ProjectLib.java")
        javaFile2.parentFile.mkdirs()
        javaFile2 << """package example;
public class ProjectLib {}
"""
        when:
        def result = build('dockerfile')

        def task = result.task(":dockerfile")

        then:
        task.outcome == TaskOutcome.SUCCESS
        def dockerFile = normalizeLineEndings(file("build/docker/main/Dockerfile").text)
        dockerFile.contains("COPY --link layers/libs /home/app/libs")
        dockerFile.contains("COPY --link layers/project_libs /home/app/libs")
        !dockerFile.contains("COPY --link layers/snapshot_libs /home/app/libs")
    }

    def "includes snapshot dependencies in different layer"() {
        def snapshotRepository = ClientAndServer.startClientAndServer()
        snapshotRepository.when(request()
                .withPath("/dummy/dummy/5.0.0-SNAPSHOT/maven-metadata.xml"))
                .respond(
                        response()
                                .withStatusCode(200)
                                .withContentType(MediaType.XML_UTF_8)
                                .withBody(snapshotList.bytes)
                )

        snapshotRepository.when(request()
                .withPath("/dummy/dummy/5.0.0-SNAPSHOT/dummy-5.0.0-${today}.${now}-1.pom"))
                .respond(
                        response()
                                .withStatusCode(200)
                                .withContentType(MediaType.XML_UTF_8)
                                .withBody(snapshotMetadata.bytes)
                )

        snapshotRepository.when(request()
                .withPath("/dummy/dummy/5.0.0-SNAPSHOT/dummy-5.0.0-${today}.${now}-1.jar"))
                .respond(
                        response()
                                .withStatusCode(200)
                                .withContentType(MediaType.APPLICATION_BINARY)
                                .withBody(new byte[0])
                )


        given:
        settingsFile << """rootProject.name = 'hello-world'"""
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.docker"
            }
            
            micronaut {
                version "$micronautVersion"
            }
            
            $repositoriesBlock

            repositories {
                maven {
                    allowInsecureProtocol = true
                    url "http://localhost:${snapshotRepository.port}"
                }        
            }
            
            application { mainClass = "example.Application" }

            dependencies {
                implementation "dummy:dummy:5.0.0-SNAPSHOT"
            }
            
            configurations.all {
                resolutionStrategy.cacheDynamicVersionsFor 0, 'seconds'
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

        testProjectDir.newFolder("project-lib", "src", "main", "java", "example")

        when:
        def result = build('dockerfile')

        def task = result.task(":dockerfile")

        then:
        task.outcome == TaskOutcome.SUCCESS
        def dockerFile = normalizeLineEndings(file("build/docker/main/Dockerfile").text)
        dockerFile.contains("COPY --link layers/libs /home/app/libs")
        dockerFile.contains("COPY --link layers/snapshot_libs /home/app/libs")
        !dockerFile.contains("COPY --link layers/project_libs /home/app/libs")

        cleanup:
        snapshotRepository.stop()

    }

    @Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/971")
    def "can disable COPY --link"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.docker"
            }
            
            micronaut {
                version "$micronautVersion"
                docker.useCopyLink = $useCopyLink
            }
            
            $repositoriesBlock

            application { mainClass = "example.Application" }
            
        """

        when:
        def result = build('dockerfile', '-s')

        def task = result.task(":dockerfile")
        def dockerFile = file("build/docker/main/Dockerfile").text

        then:
        task.outcome == TaskOutcome.SUCCESS
        dockerFile.contains("COPY --link layers/libs /home/app/libs") == useCopyLink
        dockerFile.contains("COPY layers/libs /home/app/libs") == !useCopyLink

        where:
        useCopyLink << [false, true]
    }

    private static String getSnapshotMetadata() {
        DockerBuildTaskSpec.getResourceAsStream("/dummy-metadata.xml").text
    }

    private String getSnapshotList() {
        DockerBuildTaskSpec.getResourceAsStream("/test-maven-metadata.xml").text
                .replace('xxxxxxxx', today)
                .replace('tttttt', now)

    }
}
