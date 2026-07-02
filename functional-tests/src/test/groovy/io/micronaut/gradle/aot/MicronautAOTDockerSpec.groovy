package io.micronaut.gradle.aot

import io.micronaut.gradle.DefaultVersions
import io.micronaut.gradle.AbstractGradleBuildSpec
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf
import spock.lang.Requires

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
        dockerFile = normalizeNativeDockerfileConfigDirs(dockerFile)

        then:
        dockerFile.contains("RUN mkdir -p /home/app/config-dirs/org.reactivestreams/reactive-streams/4.0.0")
        dockerFile.contains("RUN mkdir -p /home/app/config-dirs/com.fasterxml.jackson.core/jackson-annotations/4.0.0")
        dockerFile.contains("RUN mkdir -p /home/app/config-dirs/ch.qos.logback/logback-classic/4.0.0")
        dockerFile.contains("COPY --link config-dirs/org.reactivestreams/reactive-streams/4.0.0 /home/app/config-dirs/org.reactivestreams/reactive-streams/4.0.0")
        dockerFile.contains("COPY --link config-dirs/com.fasterxml.jackson.core/jackson-annotations/4.0.0 /home/app/config-dirs/com.fasterxml.jackson.core/jackson-annotations/4.0.0")
        dockerFile.contains("COPY --link config-dirs/ch.qos.logback/logback-classic/4.0.0 /home/app/config-dirs/ch.qos.logback/logback-classic/4.0.0")
        removeNativeDockerfileConfigDirLines(dockerFile) == removeNativeDockerfileConfigDirLines("""
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
            ENTRYPOINT ["/app/application"]""".stripIndent().trim())

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
