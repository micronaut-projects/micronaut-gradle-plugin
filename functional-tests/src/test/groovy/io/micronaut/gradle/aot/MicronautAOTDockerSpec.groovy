package io.micronaut.gradle.aot

import io.micronaut.gradle.DefaultVersions
import io.micronaut.gradle.AbstractGradleBuildSpec
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Requires

import java.util.jar.JarFile

class MicronautAOTDockerSpec extends AbstractAOTPluginSpec {

    def "generates an optimized docker file"() {
        withSample("aot/basic-app")

        when:
        def result = build "optimizedDockerfile", "-i"

        then:
        result.task(":prepareJitOptimizations") == null
        result.task(":optimizedDockerfile").outcome != TaskOutcome.FAILED

        def dockerFile = normalizeLineEndings(file("build/docker/optimized/Dockerfile").text)
        dockerFile == """FROM eclipse-temurin:25-jre
WORKDIR /home/app
COPY --link layers/libs /home/app/libs
COPY --link layers/app /home/app/
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/home/app/application.jar"]
"""

    }

    @Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/1379")
    def "generates an optimized docker file that trains a JDK AOT cache"() {
        withSample("aot/basic-app")
        buildFile << """
            micronaut.docker.jdkAotCache {
                enabled = true
                trainingPaths = ["/"]
            }
        """

        when:
        def result = build "optimizedDockerfile", "optimizedRunnerJitJar"

        then:
        result.task(":optimizedDockerfile").outcome != TaskOutcome.FAILED
        normalizeLineEndings(file("build/docker/optimized/Dockerfile").text) == """FROM eclipse-temurin:25-jre
WORKDIR /home/app
COPY --link layers/libs /home/app/libs
COPY --link layers/app /home/app/
EXPOSE 8080
COPY --link jdk-aot-cache/train.sh /home/app/jdk-aot-cache/train.sh
RUN ["bash", "/home/app/jdk-aot-cache/train.sh", "--cache", "/home/app/application.aot", "--timeout", "120", "--compatible-oop-compression", "--port", "8080", "--path", "/", "--", "java", "-XX:+UseG1GC", "-jar", "/home/app/application.jar"]
ENTRYPOINT ["java", "-XX:AOTCache=/home/app/application.aot", "-XX:+UseG1GC", "-jar", "/home/app/application.jar"]
"""
        file("build/docker/optimized/jdk-aot-cache/train.sh").exists()

        and: "the class path has JARs only"
        def classPath = new JarFile(file("build/libs/basic-app-0.1-optimized-runner.jar")).withCloseable {
            it.manifest.mainAttributes.getValue("Class-Path").split(" ") as List
        }
        !classPath.empty
        classPath.every { it.startsWith("libs/") && it.endsWith(".jar") }
    }

    @IgnoreIf({ os.windows })
    @IgnoreIf({ !AbstractGradleBuildSpec.dockerAvailable })
    def "generates an optimized docker image"() {
        withSample("aot/basic-app")

        when:
        def result = build "optimizedDockerBuild", "-i"

        then:
        result.task(":prepareJitOptimizations").outcome != TaskOutcome.FAILED
        result.task(":optimizedDockerfile").outcome != TaskOutcome.FAILED
        result.task(":optimizedJitJar").outcome != TaskOutcome.FAILED
        result.task(":optimizedRunnerJitJar").outcome != TaskOutcome.FAILED
        result.task(":optimizedBuildLayers").outcome != TaskOutcome.FAILED
        result.task(":optimizedDockerBuild").outcome != TaskOutcome.FAILED

        def dockerFile = normalizeLineEndings(file("build/docker/optimized/Dockerfile").text)
        dockerFile == """FROM eclipse-temurin:25-jre
WORKDIR /home/app
COPY --link layers/libs /home/app/libs
COPY --link layers/app /home/app/
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/home/app/application.jar"]
"""

    }

    @IgnoreIf({ !AbstractGradleBuildSpec.dockerAvailable })
    @Requires({ graalVmAvailable && !os.windows })
    def "generates a native optimized docker image"() {
        withSample("aot/basic-app")

        when:
        build "optimizedDockerfileNative"
        def dockerFile = normalizeLineEndings(file("build/docker/native-optimized/DockerfileNative").text)
        dockerFile = dockerFile.replaceAll("[0-9]\\.[0-9]+\\.[0-9]+", "4.0.0")
            .replaceAll("RUN native-image .*", "RUN native-image")
                .trim()

        then:
        dockerFile == """
            FROM ghcr.io/graalvm/native-image-community:25-ol${DefaultVersions.ORACLELINUX} AS graalvm
            WORKDIR /home/app
            COPY --link layers/libs /home/app/libs
            COPY --link layers/app /home/app/
            RUN mkdir /home/app/config-dirs
            COPY --link config-dirs /home/app/config-dirs
            RUN native-image
            FROM cgr.dev/chainguard/wolfi-base:latest
            EXPOSE 8080
            COPY --link --from=graalvm /home/app/application /app/application
            ENTRYPOINT ["/app/application"]""".stripIndent().trim()

        when:
        def result = build "optimizedDockerBuildNative"

        then:
        result.task(":prepareNativeOptimizations").outcome != TaskOutcome.FAILED
        result.task(":optimizedDockerfileNative").outcome != TaskOutcome.FAILED
        result.task(":optimizedNativeJar").outcome != TaskOutcome.FAILED
        result.task(":optimizedRunnerNativeJar").outcome != TaskOutcome.FAILED
        result.task(":optimizedBuildNativeLayersTask").outcome != TaskOutcome.FAILED
        result.task(":optimizedDockerfileNative").outcome != TaskOutcome.FAILED
        result.task(":optimizedDockerBuildNative").outcome != TaskOutcome.FAILED
        result.tasks.stream().noneMatch { it.path == ":nativeCompile" }

    }

}
