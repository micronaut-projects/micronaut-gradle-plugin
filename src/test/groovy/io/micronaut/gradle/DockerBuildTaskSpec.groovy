package io.micronaut.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Unroll

class DockerBuildTaskSpec extends Specification {

    @Rule TemporaryFolder testProjectDir = new TemporaryFolder()

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.newFile('settings.gradle')
        buildFile = testProjectDir.newFile('build.gradle')
    }

    def "test build docker image"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.application"
            }
            
            micronaut {
                version "2.3.3"
            }
            
            repositories {
                mavenCentral()
            }
            
            
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
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments('dockerBuild', '-s')
                .withPluginClasspath()
                .build()

        def task = result.task(":dockerBuild")
        then:
        result.output.contains("Successfully tagged hello-world:latest")
        task.outcome == TaskOutcome.SUCCESS
    }

    @Unroll
    def "test build docker native image for runtime #runtime"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.application"
            }
            
            micronaut {
                version "2.3.4"
                runtime "$runtime"
            }
            
            repositories {
                mavenCentral()
            }
            
            mainClassName="example.Application"
            
            java {
                sourceCompatibility = JavaVersion.toVersion('8')
                targetCompatibility = JavaVersion.toVersion('8')
            }
            
            dockerfileNative {
                args('-Xmx64m')
                instruction \"\"\"HEALTHCHECK CMD curl -s localhost:8090/health | grep '"status":"UP"'\"\"\"
            }
            
        """
        testProjectDir.newFolder("src", "main", "java", "example")
        def resources = testProjectDir.newFolder("src", "main", "resources")
        resources.mkdirs()
        def javaFile = testProjectDir.newFile("src/main/java/example/Application.java")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example;

class Application {
    public static void main(String... args) {
    
    }
}
"""
        def controllerFile = testProjectDir.newFile("src/main/java/example/TestController.java")
        controllerFile << """
package example;

import io.micronaut.http.annotation.*;

@Controller("/foo")
class TestController {
}
"""
        def config = testProjectDir.newFile("src/main/resources/application.yml")
        config.parentFile.mkdirs()
        config << """
micronaut:
   application:
        name: test
"""


        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments('dockerBuildNative')
                .withPluginClasspath()
                .build()

        def task = result.task(":dockerBuildNative")
        def dockerFile = new File(testProjectDir.root, 'build/docker/DockerfileNative').readLines('UTF-8')

        expect:
        dockerFile.first().startsWith(nativeImage)
        dockerFile.last() == """HEALTHCHECK CMD curl -s localhost:8090/health | grep '"status":"UP"'"""
        dockerFile.find {s -> s.contains('-Xmx64m')}

        and:
        result.output.contains("Successfully tagged hello-world:latest")
        result.output.contains("Writing resource-config.json file")
        task.outcome == TaskOutcome.SUCCESS

        where:
        runtime  | nativeImage
        "netty"  | 'FROM ghcr.io/graalvm/graalvm-ce:java'
        "lambda" | 'FROM amazonlinux:latest AS graalvm'
        "jetty"  | 'FROM ghcr.io/graalvm/graalvm-ce:java'
    }


    def "test build docker native image for lambda with custom main"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.application"
            }
            
            micronaut {
                version "2.3.4"
                runtime "lambda"
            }
            
            repositories {
                mavenCentral()
            }
            
            nativeImage {
                main = "other.Application"
            }
            
            java {
                sourceCompatibility = JavaVersion.toVersion('8')
                targetCompatibility = JavaVersion.toVersion('8')
            }
            
            mainClassName="example.Application"
        """
        testProjectDir.newFolder("src", "main", "java", "other")
        def javaFile = testProjectDir.newFile("src/main/java/other/Application.java")
        javaFile.parentFile.mkdirs()
        javaFile << """
package other;

class Application {
    public static void main(String... args) {
    
    }
}
"""

        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments('dockerBuildNative')
                .withPluginClasspath()
                .build()

        def task = result.task(":dockerBuildNative")

        expect:
        result.output.contains("Successfully tagged hello-world:latest")
        task.outcome == TaskOutcome.SUCCESS
    }

    @Unroll
    def "test construct dockerfile and dockerfileNative"() {
        setup:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.application"
            }
            
            micronaut {
                version "2.3.4"
                runtime "$runtime"
            }
            
            repositories {
                mavenCentral()
            }
            
            nativeImage {
                main = "other.Application"
            }
            
            java {
                sourceCompatibility = JavaVersion.toVersion('8')
                targetCompatibility = JavaVersion.toVersion('8')
            }
            
            dockerfile {
                args('-Xmx64m')
                baseImage('test_base_image_jvm')
                instruction \"\"\"HEALTHCHECK CMD curl -s localhost:8090/health | grep '"status":"UP"'\"\"\"
            }
            dockerfileNative {
                args('-Xmx64m')
                baseImage('test_base_image_docker')
                instruction \"\"\"HEALTHCHECK CMD curl -s localhost:8090/health | grep '"status":"UP"'\"\"\"
            }
            
            mainClassName="example.Application"
        """
        testProjectDir.newFolder("src", "main", "java", "other")
        def javaFile = testProjectDir.newFile("src/main/java/other/Application.java")
        javaFile.parentFile.mkdirs()
        javaFile << """
package other;

class Application {
    public static void main(String... args) {
    
    }
}
"""

        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments('dockerfile', 'dockerfileNative')
                .withPluginClasspath()
                .build()

        def dockerfileTask = result.task(":dockerfile")
        def dockerfileNativeTask = result.task(":dockerfileNative")
        def dockerFileNative = new File(testProjectDir.root, 'build/docker/DockerfileNative').readLines('UTF-8')
        def dockerFile = new File(testProjectDir.root, 'build/docker/Dockerfile').readLines('UTF-8')

        expect:
        dockerfileTask.outcome == TaskOutcome.SUCCESS
        dockerfileNativeTask.outcome == TaskOutcome.SUCCESS

        and:
        dockerFile.first() == ('FROM test_base_image_jvm')
        dockerFile.last() == """HEALTHCHECK CMD curl -s localhost:8090/health | grep '"status":"UP"'"""
        dockerFile.find {s -> s.contains('-Xmx64m')}

        and:
        dockerFileNative.find() { s -> s == 'FROM test_base_image_docker' }
        dockerFileNative.last() == """HEALTHCHECK CMD curl -s localhost:8090/health | grep '"status":"UP"'"""
        dockerFileNative.find {s -> s.contains('-Xmx64m')}

        where:
        runtime  | nativeImage
        "netty"  | 'FROM ghcr.io/graalvm/graalvm-ce:java'
        "lambda" | 'FROM amazonlinux:latest AS graalvm'
        "jetty"  | 'FROM ghcr.io/graalvm/graalvm-ce:java'
    }

    @Unroll
    void 'build mostly static native images when using distroless docker image for runtime=#runtime'() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.application"
            }

            micronaut {
                version "2.3.4"

                runtime "$runtime"
            }

            repositories {
                mavenCentral()
            }

            application {
                mainClass.set("com.example.Application")
            }

            java {
                sourceCompatibility = JavaVersion.toVersion('8')
                targetCompatibility = JavaVersion.toVersion('8')
            }

            dockerfileNative {
                baseImage("gcr.io/distroless/cc-debian10")
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments('dockerfileNative')
            .withPluginClasspath()
            .build()

        def dockerfileNativeTask = result.task(':dockerfileNative')
        def dockerFileNative = new File(testProjectDir.root, 'build/docker/DockerfileNative').readLines('UTF-8')

        then:
        dockerfileNativeTask.outcome == TaskOutcome.SUCCESS

        and:
        dockerFileNative.find {s -> s.contains('FROM gcr.io/distroless/cc-debian10')}
        dockerFileNative.find {s -> s.contains('-H:+StaticExecutableWithDynamicLibC')}

        where:
        runtime << ['netty', 'lambda']
    }

    void 'use alpine-glibc by default and do not build mostly static native images'() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.application"
            }

            micronaut {
                version "2.3.4"
                runtime "netty"
            }

            repositories {
                mavenCentral()
            }

            application {
                mainClass.set("com.example.Application")
            }

            java {
                sourceCompatibility = JavaVersion.toVersion('8')
                targetCompatibility = JavaVersion.toVersion('8')
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments('dockerfileNative')
            .withPluginClasspath()
            .build()

        def dockerfileNativeTask = result.task(':dockerfileNative')
        def dockerFileNative = new File(testProjectDir.root, 'build/docker/DockerfileNative').readLines('UTF-8')

        then:
        dockerfileNativeTask.outcome == TaskOutcome.SUCCESS

        and:
        dockerFileNative.find {s -> s.contains('FROM frolvlad/alpine-glibc:alpine-3.12')}
        dockerFileNative.find {s -> !s.contains('-H:+StaticExecutableWithDynamicLibC')}
    }

    void 'do not use alpine-glibc for lambda runtime'() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.application"
            }

            micronaut {
                version "2.3.0"
                runtime "lambda"
            }

            repositories {
                mavenCentral()
            }

            application {
                mainClass.set("com.example.Application")
            }

            java {
                sourceCompatibility = JavaVersion.toVersion('8')
                targetCompatibility = JavaVersion.toVersion('8')
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments('dockerfileNative')
            .withPluginClasspath()
            .build()

        def dockerfileNativeTask = result.task(':dockerfileNative')
        def dockerFileNative = new File(testProjectDir.root, 'build/docker/DockerfileNative').readLines('UTF-8')

        then:
        dockerfileNativeTask.outcome == TaskOutcome.SUCCESS

        and:
        dockerFileNative.find {s -> !s.contains('FROM frolvlad/alpine-glibc:alpine-3.12')}
        dockerFileNative.find {s -> !s.contains('-H:+StaticExecutableWithDynamicLibC')}
    }
}
