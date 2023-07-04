package io.micronaut.gradle.docker

import io.micronaut.gradle.AbstractGradleBuildSpec
import io.micronaut.gradle.DefaultVersions
import io.micronaut.gradle.fixtures.AbstractEagerConfiguringFunctionalTest
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Requires

@Requires({ AbstractGradleBuildSpec.graalVmAvailable })
@IgnoreIf({ os.windows })
class DockerNativeFunctionalTest extends AbstractEagerConfiguringFunctionalTest {

    def "test build docker native image for runtime #runtime"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        println settingsFile.text
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.docker"
                id "io.micronaut.graalvm"
            }
            
            micronaut {
                version "$micronautVersion"
                runtime "$runtime"
            }
            
            $repositoriesBlock
            
            mainClassName="example.Application"
            
            java {
                sourceCompatibility = JavaVersion.toVersion('17')
                targetCompatibility = JavaVersion.toVersion('17')
            }
            
            dockerfileNative {
                args('-Xmx64m')
                instruction \"\"\"HEALTHCHECK CMD curl -s localhost:8090/health | grep '"status":"UP"'\"\"\"
            }
            
            graalvmNative.binaries.all {
                buildArgs.addAll(["--exclude-config", "micronaut-function-aws-api-proxy-.*.jar", "META-INF/native-image/.*.properties"])
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


        def result = build('dockerBuildNative')

        def task = result.task(":dockerBuildNative")
        def dockerFile = new File(testProjectDir.root, 'build/docker/native-main/DockerfileNative').readLines('UTF-8')

        expect:
        dockerFile.first().startsWith(nativeImage)
        dockerFile.find { s -> s == """HEALTHCHECK CMD curl -s localhost:8090/health | grep '"status":"UP"'""" }
        dockerFile.last().contains('ENTRYPOINT')
        dockerFile.find { s -> s.contains('-Xmx64m') }

        and:
        result.output.contains("Successfully tagged hello-world:latest")
        result.output.contains("Resources configuration written into")
        task.outcome == TaskOutcome.SUCCESS

        where:
        runtime           | nativeImage
        "netty"           | "FROM ghcr.io/graalvm/native-image-community:17-ol${DefaultVersions.ORACLELINUX}"
        "lambda_provided" | 'FROM amazonlinux:2 AS graalvm'
        "jetty"           | "FROM ghcr.io/graalvm/native-image-community:17-ol${DefaultVersions.ORACLELINUX}"
    }

    void 'build mostly static native images when using distroless docker image for runtime=#runtime'() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.docker"
                id "io.micronaut.graalvm"
            }

            micronaut {
                version "$micronautVersion"

                runtime "$runtime"
            }

            $repositoriesBlock

            application {
                mainClass.set("com.example.Application")
            }

            java {
                sourceCompatibility = JavaVersion.toVersion('17')
                targetCompatibility = JavaVersion.toVersion('17')
            }

            dockerfileNative {
                baseImage("gcr.io/distroless/cc-debian10")
            }

            graalvmNative.binaries.all {
                buildArgs.addAll(["--exclude-config", "micronaut-function-aws-api-proxy-.*.jar", "META-INF/native-image/.*.properties"])
            }
        """

        when:
        def result = build('dockerfileNative')

        def dockerfileNativeTask = result.task(':dockerfileNative')
        def dockerFileNative = new File(testProjectDir.root, 'build/docker/native-main/DockerfileNative').readLines('UTF-8')

        then:
        dockerfileNativeTask.outcome == TaskOutcome.SUCCESS

        and:
        dockerFileNative.find { s -> s.contains('FROM gcr.io/distroless/cc-debian10') }
        dockerFileNative.find { s -> s.contains('-H:+StaticExecutableWithDynamicLibC') }

        where:
        runtime << ['netty', 'lambda_provided']
    }

    void 'use alpine-glibc by default and do not build mostly static native images'() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.docker"
                id "io.micronaut.graalvm"
            }

            micronaut {
                version "$micronautVersion"
                runtime "netty"
            }

            $repositoriesBlock

            application {
                mainClass.set("com.example.Application")
            }

            java {
                sourceCompatibility = JavaVersion.toVersion('17')
                targetCompatibility = JavaVersion.toVersion('17')
            }
        """

        when:
        def result = build('dockerfileNative')

        def dockerfileNativeTask = result.task(':dockerfileNative')
        def dockerFileNative = new File(testProjectDir.root, 'build/docker/native-main/DockerfileNative').readLines('UTF-8')

        then:
        dockerfileNativeTask.outcome == TaskOutcome.SUCCESS

        and:
        dockerFileNative.find { s -> s.contains('FROM frolvlad/alpine-glibc:alpine-3.12') }
        dockerFileNative.find { s -> !s.contains('-H:+StaticExecutableWithDynamicLibC') }
    }

    void 'do not use alpine-glibc for lambda_provided runtime'() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.docker"
                id "io.micronaut.graalvm"
            }

            micronaut {
                version "$micronautVersion"
                runtime "lambda_provided"
            }

            $repositoriesBlock

            application {
                mainClass.set("com.example.Application")
            }

            java {
                sourceCompatibility = JavaVersion.toVersion('17')
                targetCompatibility = JavaVersion.toVersion('17')
            }
        """

        when:
        def result = build('dockerfileNative')

        def dockerfileNativeTask = result.task(':dockerfileNative')
        def dockerFileNative = new File(testProjectDir.root, 'build/docker/native-main/DockerfileNative').readLines('UTF-8')

        then:
        dockerfileNativeTask.outcome == TaskOutcome.SUCCESS

        and:
        dockerFileNative.find { s -> !s.contains('FROM frolvlad/alpine-glibc:alpine-3.12') }
        dockerFileNative.find { s -> !s.contains('-H:+StaticExecutableWithDynamicLibC') }
    }

    def "test build docker native image for lambda_provided with custom main"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.docker"
                id "io.micronaut.graalvm"
            }
            
            micronaut {
                version "$micronautVersion"
                runtime "lambda_provided"
            }
            
            $repositoriesBlock
            
            graalvmNative {
                binaries {
                    main {
                        mainClass =  "other.Application"
                    }
                }
            }
            
            java {
                sourceCompatibility = JavaVersion.toVersion('17')
                targetCompatibility = JavaVersion.toVersion('17')
            }
            
            mainClassName="example.Application"

            graalvmNative.binaries.all {
                buildArgs.addAll(["--exclude-config", "micronaut-function-aws-api-proxy-.*.jar", "META-INF/native-image/.*.properties"])
            }
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

        def result = build('dockerBuildNative')

        def task = result.task(":dockerBuildNative")

        expect:
        result.output.contains("Successfully tagged hello-world:latest")
        task.outcome == TaskOutcome.SUCCESS
    }

    def "test construct dockerfile and dockerfileNative custom entrypoint"() {
        setup:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.docker"
                id "io.micronaut.graalvm"
            }
            
            micronaut {
                version "$micronautVersion"
            }
            
            $repositoriesBlock
            
            graalvmNative {
                binaries {
                    main {
                        mainClass = "other.Application"
                    }
                }
            }
            
            java {
                sourceCompatibility = JavaVersion.toVersion('17')
                targetCompatibility = JavaVersion.toVersion('17')
            }
            
            dockerfile {
                args('-Xmx64m')
                baseImage('test_base_image_jvm')
                instruction \"\"\"HEALTHCHECK CMD curl -s localhost:8090/health | grep '"status":"UP"'\"\"\"
                entryPoint('./entrypoint.sh')
            }
            dockerfileNative {
                args('-Xmx64m')
                baseImage('test_base_image_docker')
                instruction \"\"\"HEALTHCHECK CMD curl -s localhost:8090/health | grep '"status":"UP"'\"\"\"
                entryPoint('./entrypoint.sh')
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

        def result = build('dockerfile', 'dockerfileNative')

        def dockerfileTask = result.task(":dockerfile")
        def dockerfileNativeTask = result.task(":dockerfileNative")
        def dockerFileNative = new File(testProjectDir.root, 'build/docker/native-main/DockerfileNative').readLines('UTF-8')
        def dockerFile = new File(testProjectDir.root, 'build/docker/main/Dockerfile').readLines('UTF-8')

        expect:
        dockerfileTask.outcome == TaskOutcome.SUCCESS
        dockerfileNativeTask.outcome == TaskOutcome.SUCCESS

        and:
        dockerFile.first() == ('FROM test_base_image_jvm')
        dockerFile.find { s -> s == """HEALTHCHECK CMD curl -s localhost:8090/health | grep '"status":"UP"'""" }
        dockerFile.find { s -> s == 'ENTRYPOINT ["./entrypoint.sh"]' }

        and:
        dockerFileNative.find() { s -> s == 'FROM test_base_image_docker' }
        dockerFileNative.find { s -> s == """HEALTHCHECK CMD curl -s localhost:8090/health | grep '"status":"UP"'""" }
        dockerFileNative.find { s -> s == 'ENTRYPOINT ["./entrypoint.sh"]' }
    }

    void 'test build native docker file'() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.docker"
                id "io.micronaut.graalvm"
            }
            
            micronaut {
                version "$micronautVersion"
                runtime "netty"
                testRuntime "junit5"
            }
            
            $repositoriesBlock
            
            dependencies {
                runtimeOnly("ch.qos.logback:logback-classic")
                testImplementation("io.micronaut:micronaut-http-client")
            }
            mainClassName="example.Application"
            
        """

        when:
        def result = build('javaToolchains', 'dockerfileNative')

        def task = result.task(":dockerfileNative")
        println(result.output)

        then:
        task.outcome == TaskOutcome.SUCCESS
        result.output.contains("Dockerfile written to")
        result.output.contains("build/docker/native-main/DockerfileNative")

        new File("$testProjectDir.root/build/docker/native-main/DockerfileNative").text.count("-cp") == 1
    }

    def "test construct dockerfile and dockerfileNative"() {
        setup:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.docker"
                id "io.micronaut.graalvm"
            }
            
            micronaut {
                version "$micronautVersion"
                runtime "$runtime"
            }
            
            $repositoriesBlock
            
            graalvmNative {
                binaries {
                    main {
                        mainClass =  "other.Application"
                    }
                }
            }
                    
            java {
                sourceCompatibility = JavaVersion.toVersion('17')
                targetCompatibility = JavaVersion.toVersion('17')
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

        def result = build('dockerfile', 'dockerfileNative')

        def dockerfileTask = result.task(":dockerfile")
        def dockerfileNativeTask = result.task(":dockerfileNative")
        def dockerFileNative = new File(testProjectDir.root, 'build/docker/native-main/DockerfileNative').readLines('UTF-8')
        def dockerFile = new File(testProjectDir.root, 'build/docker/main/Dockerfile').readLines('UTF-8')

        expect:
        dockerfileTask.outcome == TaskOutcome.SUCCESS
        dockerfileNativeTask.outcome == TaskOutcome.SUCCESS

        and:
        dockerFile.first() == ('FROM test_base_image_jvm')
        dockerFile.find { s -> s == """HEALTHCHECK CMD curl -s localhost:8090/health | grep '"status":"UP"'""" }
        dockerFile.last().contains('ENTRYPOINT')
        dockerFile.find { s -> s.contains('-Xmx64m') }

        and:
        dockerFileNative.find() { s -> s == 'FROM test_base_image_docker' }
        dockerFileNative.find { s -> s == """HEALTHCHECK CMD curl -s localhost:8090/health | grep '"status":"UP"'""" }
        dockerFileNative.last().contains('ENTRYPOINT')
        dockerFileNative.find { s -> s.contains('-Xmx64m') }

        where:
        runtime           | nativeImage
        "netty"           | "FROM ghcr.io/graalvm/native-image-community:17-ol${DefaultVersions.ORACLELINUX}"
        "lambda_provided" | 'FROM amazonlinux:2 AS graalvm'
        "jetty"           | "FROM ghcr.io/graalvm/native-image-community:17-ol${DefaultVersions.ORACLELINUX}"
    }

    @Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/402")
    def "can configure an alternate working directory"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """import io.micronaut.gradle.docker.DockerBuildOptions
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.docker"
                id "io.micronaut.graalvm"
            }
            
            micronaut {
                version "$micronautVersion"
                runtime "netty"
            }
            
            $repositoriesBlock
            
            mainClassName="example.Application"
            
            java {
                sourceCompatibility = JavaVersion.toVersion('17')
                targetCompatibility = JavaVersion.toVersion('17')
            }
            
            dockerfileNative {
                args('-Xmx64m')
                instruction \"\"\"HEALTHCHECK CMD curl -s localhost:8090/health | grep '"status":"UP"'\"\"\"
            }

            tasks.withType(DockerBuildOptions).configureEach {
                targetWorkingDirectory = "/home/alternate"
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

        when:
        build "dockerfileNative"
        def dockerFile = normalizeLineEndings(file("build/docker/native-main/DockerfileNative").text)
        dockerFile = dockerFile.replaceAll("[0-9]\\.[0-9]+\\.[0-9]+", "4.0.0")
            .trim()

        then:
        dockerFile == """
FROM ghcr.io/graalvm/native-image-community:17-ol${DefaultVersions.ORACLELINUX} AS graalvm
WORKDIR /home/alternate
COPY layers/libs /home/alternate/libs
COPY layers/classes /home/alternate/classes
COPY layers/resources /home/alternate/resources
COPY layers/application.jar /home/alternate/application.jar
RUN mkdir /home/alternate/config-dirs
RUN mkdir -p /home/alternate/config-dirs/generateResourcesConfigFile
RUN mkdir -p /home/alternate/config-dirs/io.netty/netty-common/4.0.0.Final
COPY config-dirs/generateResourcesConfigFile /home/alternate/config-dirs/generateResourcesConfigFile
COPY config-dirs/io.netty/netty-common/4.0.0.Final /home/alternate/config-dirs/io.netty/netty-common/4.0.0.Final
RUN native-image --exclude-config .*/libs/netty-transport-4.0.0.Final.jar ^/META-INF/native-image/.* --exclude-config .*/libs/netty-handler-4.0.0.Final.jar ^/META-INF/native-image/.* --exclude-config .*/libs/netty-buffer-4.0.0.Final.jar ^/META-INF/native-image/.* --exclude-config .*/libs/netty-common-4.0.0.Final.jar ^/META-INF/native-image/.* --exclude-config .*/libs/netty-codec-http-4.0.0.Final.jar ^/META-INF/native-image/.* --exclude-config .*/libs/netty-codec-http2-4.0.0.Final.jar ^/META-INF/native-image/.* -cp /home/alternate/libs/*.jar:/home/alternate/resources:/home/alternate/application.jar --no-fallback -H:Name=application -H:ConfigurationFileDirectories=/home/alternate/config-dirs/generateResourcesConfigFile,/home/alternate/config-dirs/io.netty/netty-buffer/4.0.0.Final,/home/alternate/config-dirs/io.netty/netty-common/4.0.0.Final,/home/alternate/config-dirs/io.netty/netty-codec-http/4.0.0.Final,/home/alternate/config-dirs/io.netty/netty-transport/4.0.0.Final,/home/alternate/config-dirs/io.netty/netty-handler/4.0.0.Final,/home/alternate/config-dirs/io.netty/netty-codec-http2/4.0.0.Final -H:Class=example.Application
FROM frolvlad/alpine-glibc:alpine-3.12
RUN apk --no-cache update && apk add libstdc++
EXPOSE 8080
HEALTHCHECK CMD curl -s localhost:8090/health | grep '"status":"UP"'
COPY --from=graalvm /home/alternate/application /app/application
ENTRYPOINT ["/app/application", "-Xmx64m"]
""".trim()

        when:
        def result = build ":dockerBuildNative"
        def task = result.task(":dockerBuildNative")

        then:
        task.outcome == TaskOutcome.SUCCESS

    }

    @Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/373")
    def "docker plugin shouldn't assume nativeCompile is available"() {
        settingsFile << """
            rootProject.name = 'hello-world'
        """
        buildFile << """plugins {
  id 'io.micronaut.minimal.application'
  id 'io.micronaut.docker'
}

micronaut {
    version "$micronautVersion"
    runtime "netty"
}
            
$repositoriesBlock

mainClassName="example.Application"

// Force realization of all tasks to trigger the problem
afterEvaluate {
    project.tasks.forEach(task -> println(task.getName()))
}
"""

        when:
        def result = build('dockerfile')

        def task = result.task(":dockerfile")

        then:
        task.outcome == TaskOutcome.SUCCESS
    }

    @Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/667")
    def "can tweak the generated docker file"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.docker"
                id "io.micronaut.graalvm"
            }
            
            $repositoriesBlock

            micronaut {
                version "$micronautVersion"
            }

            mainClassName="example.Application"

            tasks.withType(io.micronaut.gradle.docker.DockerBuildOptions).configureEach {
                editDockerfile {
                    after('COPY layers/libs /home/app/libs') {
                        insert('COPY server.iprof /home/app/server.iprof')
                    } 
                }
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
        def dockerfile = new File(testProjectDir.root, 'build/docker/main/Dockerfile').text
        dockerfile == """FROM openjdk:17-alpine
WORKDIR /home/app
COPY layers/libs /home/app/libs
COPY server.iprof /home/app/server.iprof
COPY layers/classes /home/app/classes
COPY layers/resources /home/app/resources
COPY layers/application.jar /home/app/application.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/home/app/application.jar"]
"""

        when:
        result = build('dockerfileNative', '-s')

        then:
        def dockerfileNative = new File(testProjectDir.root, 'build/docker/native-main/DockerfileNative').text
        dockerfileNative == """FROM ghcr.io/graalvm/native-image-community:17-ol${DefaultVersions.ORACLELINUX} AS graalvm
WORKDIR /home/app
COPY layers/libs /home/app/libs
COPY server.iprof /home/app/server.iprof
COPY layers/classes /home/app/classes
COPY layers/resources /home/app/resources
COPY layers/application.jar /home/app/application.jar
RUN mkdir /home/app/config-dirs
RUN mkdir -p /home/app/config-dirs/generateResourcesConfigFile
COPY config-dirs/generateResourcesConfigFile /home/app/config-dirs/generateResourcesConfigFile
RUN native-image -cp /home/app/libs/*.jar:/home/app/resources:/home/app/application.jar --no-fallback -H:Name=application -H:ConfigurationFileDirectories=/home/app/config-dirs/generateResourcesConfigFile -H:Class=example.Application
FROM frolvlad/alpine-glibc:alpine-3.12
RUN apk --no-cache update && apk add libstdc++
EXPOSE 8080
COPY --from=graalvm /home/app/application /app/application
ENTRYPOINT ["/app/application"]
"""
    }

    @Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/667")
    def "dockerfile tweaks participate in up-to-date checking"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.docker"
                id "io.micronaut.graalvm"
            }
            
            $repositoriesBlock

            micronaut {
                version "$micronautVersion"
            }

            mainClassName="example.Application"

            tasks.withType(io.micronaut.gradle.docker.DockerBuildOptions).configureEach {
                editDockerfile {
                    after('COPY layers/libs /home/app/libs') {
                        insert('COPY server.iprof /home/app/server.iprof')
                    } 
                }
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
        def dockerfile = new File(testProjectDir.root, 'build/docker/main/Dockerfile').text
        dockerfile == """FROM openjdk:17-alpine
WORKDIR /home/app
COPY layers/libs /home/app/libs
COPY server.iprof /home/app/server.iprof
COPY layers/classes /home/app/classes
COPY layers/resources /home/app/resources
COPY layers/application.jar /home/app/application.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/home/app/application.jar"]
"""

        when:
        result = build('dockerfile', '-s')

        then:
        result.task(":dockerfile").outcome == TaskOutcome.UP_TO_DATE

        when:
        buildFile << """
            tasks.withType(io.micronaut.gradle.docker.DockerBuildOptions).configureEach {
                editDockerfile {
                    after('COPY server.iprof /home/app/server.iprof') {
                        insert('COPY README.TXT /home/app/README.TXT')
                    } 
                }
            }
        """
        result = build('dockerfile', '-s')

        then:
        result.task(":dockerfile").outcome == TaskOutcome.SUCCESS

    }

    @Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/756")
    def "should not generate empty RUN command"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.docker"
                id "io.micronaut.graalvm"
            }

            micronaut {
                version "$micronautVersion"

                runtime "netty"
            }

            $repositoriesBlock

            application {
                mainClass.set("com.example.Application")
            }

            tasks.named("dockerfileNative") {
                baseImage('ubuntu:22.04')
                instruction 'RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*'
                instruction 'HEALTHCHECK CMD curl -s localhost:8080/endpoints/health | grep \\'"status":"UP"\\''
            }
            
        """

        when:
        def result = build('dockerfileNative')
        def dockerFileNative = new File(testProjectDir.root, 'build/docker/native-main/DockerfileNative').readLines('UTF-8')
        def emptyRunLines = dockerFileNative.findAll { it == "RUN " }

        then:
        emptyRunLines.isEmpty()
    }

}
