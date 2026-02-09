package io.micronaut.gradle.docker

import io.micronaut.gradle.DefaultVersions
import io.micronaut.gradle.fixtures.AbstractEagerConfiguringFunctionalTest
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Requires

@Requires({ graalVmAvailable })
@IgnoreIf({ os.windows })
class DockerNativeFunctionalTest extends AbstractEagerConfiguringFunctionalTest {

    @Lazy
    String defaultBaseImage = 'cgr.dev/chainguard/wolfi-base:latest'

    @Lazy
    String defaultDockerFrom = "FROM $defaultBaseImage"

    def "test build docker native image for runtime #runtime (JDK #jdk)"() {
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
             
            application { mainClass = "example.Application" }
             
            java {
                sourceCompatibility = JavaVersion.toVersion('17')
                targetCompatibility = JavaVersion.toVersion('17')
            }
            
            dockerfileNative {
                args('-Xmx64m')
                instruction \"\"\"HEALTHCHECK CMD curl -s localhost:8090/health | grep '"status":"UP"'\"\"\"
                jdkVersion = "$jdk"
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
        runtime           | jdk | nativeImage
        "netty"           | 17  | "FROM ghcr.io/graalvm/native-image-community:17-ol${DefaultVersions.ORACLELINUX}"
        "lambda_provided" | 17  | "FROM public.ecr.aws/amazonlinux/amazonlinux:${DefaultVersions.AMAZONLINUX} AS graalvm"
        "lambda_provided" | 21  | "FROM public.ecr.aws/amazonlinux/amazonlinux:${DefaultVersions.AMAZONLINUX} AS graalvm"
        "jetty"           | 17  | "FROM ghcr.io/graalvm/native-image-community:17-ol${DefaultVersions.ORACLELINUX}"
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

    void 'can set jdkVersion as #jdkVersion with #dsl DSL for native images'() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        if (kotlinDsl) {
            kotlinBuildFile << """
                plugins {
                    id("io.micronaut.minimal.application")
                    id("io.micronaut.docker")
                    id("io.micronaut.graalvm")
                }

                micronaut {
                    version("$micronautVersion")
                    runtime("netty")
                }

                $repositoriesBlock

                application {
                    mainClass.set("com.example.Application")
                }

                tasks.named<io.micronaut.gradle.docker.NativeImageDockerfile>("dockerfileNative") {
                   jdkVersion.set("$jdkVersion")
                }
            """.stripIndent()
        } else {
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

                dockerfileNative {
                    jdkVersion = '$jdkVersion'
                }
            """.stripIndent()
        }

        when:
        def result = build('dockerfileNative')

        def dockerfileNativeTask = result.task(':dockerfileNative')
        def dockerFileNative = new File(testProjectDir.root, 'build/docker/native-main/DockerfileNative').readLines('UTF-8')

        then:
        dockerfileNativeTask.outcome == TaskOutcome.SUCCESS

        and:
        dockerFileNative.find { s -> s.contains("FROM ghcr.io/graalvm/native-image-community:$expected AS graalvm") }

        where:
        jdkVersion | kotlinDsl || expected
        '21'       | true      || '21-ol9'
        '21'       | false     || '21-ol9'
        '17'       | true      || '17-ol9'
        '17'       | false     || '17-ol9'

        dsl = kotlinDsl ? 'kotlin' : 'groovy'
    }

    void 'use wolfi-base by default and do not build mostly static native images'() {
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
        dockerFileNative.find { s -> s.contains(defaultBaseImage) }
        dockerFileNative.find { s -> !s.contains('-H:+StaticExecutableWithDynamicLibC') }
    }

    void 'do not use wolfi-base for lambda_provided runtime'() {
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
        dockerFileNative.find { s -> !s.contains('FROM cgr.dev/chainguard/wolfi-base:latest') }
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
            
            application { mainClass = "example.Application" }

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
            
            application { mainClass = "example.Application" }
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
            application { mainClass = "example.Application" }
             
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
            
            application { mainClass = "example.Application" }
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
        "lambda_provided" | 'FROM amazonlinux:2023 AS graalvm'
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
            
            application { mainClass = "example.Application" }
            
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
                .replaceAll("RUN native-image .*", "RUN native-image")
                .trim()

        then:
        dockerFile == """
            FROM ghcr.io/graalvm/native-image-community:17-ol${DefaultVersions.ORACLELINUX} AS graalvm
            WORKDIR /home/alternate
            COPY --link layers/libs /home/alternate/libs
            COPY --link layers/app /home/alternate/
            COPY --link layers/resources /home/alternate/resources
            RUN mkdir /home/alternate/config-dirs
            RUN mkdir -p /home/alternate/config-dirs/generateResourcesConfigFile
            RUN mkdir -p /home/alternate/config-dirs/io.netty/netty-common/4.0.0.Final
            RUN mkdir -p /home/alternate/config-dirs/io.netty/netty-transport/4.0.0.Final
            COPY --link config-dirs/generateResourcesConfigFile /home/alternate/config-dirs/generateResourcesConfigFile
            COPY --link config-dirs/io.netty/netty-common/4.0.0.Final /home/alternate/config-dirs/io.netty/netty-common/4.0.0.Final
            COPY --link config-dirs/io.netty/netty-transport/4.0.0.Final /home/alternate/config-dirs/io.netty/netty-transport/4.0.0.Final
            RUN native-image
            FROM cgr.dev/chainguard/wolfi-base:latest
            EXPOSE 8080
            HEALTHCHECK CMD curl -s localhost:8090/health | grep '"status":"UP"'
            COPY --link --from=graalvm /home/alternate/application /app/application
            ENTRYPOINT ["/app/application", "-Xmx64m"]""".stripIndent().trim()

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

application { mainClass = "example.Application" }

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

            application { mainClass = "example.Application" }

            tasks.withType(io.micronaut.gradle.docker.DockerBuildOptions).configureEach {
                editDockerfile {
                    after('COPY --link layers/libs /home/app/libs') {
                        insert('COPY --link server.iprof /home/app/server.iprof')
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
        dockerfile == """FROM eclipse-temurin:17-jre
WORKDIR /home/app
COPY --link layers/libs /home/app/libs
COPY --link server.iprof /home/app/server.iprof
COPY --link layers/app /home/app/
COPY --link layers/resources /home/app/resources
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/home/app/application.jar"]
"""

        when:
        result = build('dockerfileNative', '-s')

        then:
        def dockerfileNative = new File(testProjectDir.root, 'build/docker/native-main/DockerfileNative').text
        dockerfileNative == """FROM ghcr.io/graalvm/native-image-community:17-ol${DefaultVersions.ORACLELINUX} AS graalvm
WORKDIR /home/app
COPY --link layers/libs /home/app/libs
COPY --link server.iprof /home/app/server.iprof
COPY --link layers/app /home/app/
COPY --link layers/resources /home/app/resources
RUN mkdir /home/app/config-dirs
RUN mkdir -p /home/app/config-dirs/generateResourcesConfigFile
COPY --link config-dirs/generateResourcesConfigFile /home/app/config-dirs/generateResourcesConfigFile
RUN native-image -cp /home/app/libs/*.jar:/home/app/resources:/home/app/application.jar --no-fallback -o application -H:ConfigurationFileDirectories=/home/app/config-dirs/generateResourcesConfigFile example.Application
${defaultDockerFrom}
EXPOSE 8080
COPY --link --from=graalvm /home/app/application /app/application
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

            application { mainClass = "example.Application" }

            tasks.withType(io.micronaut.gradle.docker.DockerBuildOptions).configureEach {
                editDockerfile {
                    after('COPY --link layers/libs /home/app/libs') {
                        insert('COPY --link server.iprof /home/app/server.iprof')
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
        dockerfile == """FROM eclipse-temurin:17-jre
WORKDIR /home/app
COPY --link layers/libs /home/app/libs
COPY --link server.iprof /home/app/server.iprof
COPY --link layers/app /home/app/
COPY --link layers/resources /home/app/resources
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
                    after('COPY --link server.iprof /home/app/server.iprof') {
                        insert('COPY --link README.TXT /home/app/README.TXT')
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
