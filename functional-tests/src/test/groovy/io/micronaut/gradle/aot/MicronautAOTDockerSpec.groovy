package io.micronaut.gradle.aot

import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf
import spock.lang.Requires

@IgnoreIf({ os.windows })
@Requires({ jvm.isJava11Compatible() })
class MicronautAOTDockerSpec extends AbstractAOTPluginSpec {

    def "generates an optimized docker file"() {
        withSample("aot/basic-app")

        when:
        def result = build "optimizedDockerfile", "-i"

        then:
        result.task(":prepareJitOptimizations") == null
        result.task(":optimizedDockerfile").outcome == TaskOutcome.SUCCESS

        def dockerFile = normalizeLineEndings(file("build/docker/optimized/Dockerfile").text)
        dockerFile == """FROM openjdk:17-alpine
WORKDIR /home/app
COPY layers/libs /home/app/libs
COPY layers/classes /home/app/classes
COPY layers/resources /home/app/resources
COPY layers/application.jar /home/app/application.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/home/app/application.jar"]
"""

    }

    def "generates an optimized docker image"() {
        withSample("aot/basic-app")

        when:
        def result = build "optimizedDockerBuild", "-i"

        then:
        result.task(":prepareJitOptimizations").outcome == TaskOutcome.SUCCESS
        result.task(":optimizedDockerfile").outcome == TaskOutcome.SUCCESS
        result.task(":optimizedJitJar").outcome == TaskOutcome.SUCCESS
        result.task(":optimizedRunnerJitJar").outcome == TaskOutcome.SUCCESS
        result.task(":optimizedBuildLayers").outcome == TaskOutcome.SUCCESS
        result.task(":optimizedDockerBuild").outcome == TaskOutcome.SUCCESS

        def dockerFile = normalizeLineEndings(file("build/docker/optimized/Dockerfile").text)
        dockerFile == """FROM openjdk:17-alpine
WORKDIR /home/app
COPY layers/libs /home/app/libs
COPY layers/classes /home/app/classes
COPY layers/resources /home/app/resources
COPY layers/application.jar /home/app/application.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/home/app/application.jar"]
"""

    }

    @Requires({ jvm.isJava11() })
    def "generates a native optimized docker image"() {
        withSample("aot/basic-app")

        when:
        def result = build "optimizedDockerBuildNative", "-i"

        then:
        result.task(":prepareNativeOptimizations").outcome == TaskOutcome.SUCCESS
        result.task(":optimizedDockerfileNative").outcome == TaskOutcome.SUCCESS
        result.task(":optimizedNativeJar").outcome == TaskOutcome.SUCCESS
        result.task(":optimizedRunnerNativeJar").outcome == TaskOutcome.SUCCESS
        result.task(":optimizedBuildNativeLayersTask").outcome == TaskOutcome.SUCCESS
        result.task(":optimizedDockerfileNative").outcome == TaskOutcome.SUCCESS
        result.task(":optimizedDockerBuildNative").outcome == TaskOutcome.SUCCESS

        def dockerFile = normalizeLineEndings(file("build/docker/native-optimized/DockerfileNative").text)
        dockerFile == """FROM ghcr.io/graalvm/native-image:java11-21.3.0 AS graalvm
WORKDIR /home/app
COPY layers/libs /home/app/libs
COPY layers/classes /home/app/classes
COPY layers/resources /home/app/resources
COPY layers/application.jar /home/app/application.jar
RUN mkdir /home/app/config-dirs
COPY config-dirs/generateResourcesConfigFile /home/app/config-dirs/generateResourcesConfigFile
RUN native-image -cp /home/app/libs/*.jar:/home/app/resources:/home/app/application.jar --no-fallback -H:Name=application -H:ConfigurationFileDirectories=/home/app/config-dirs/generateResourcesConfigFile -H:Class=demo.app.Application
FROM frolvlad/alpine-glibc:alpine-3.12
RUN apk update && apk add libstdc++
COPY --from=graalvm /home/app/application /app/application
ENTRYPOINT ["/app/application"]
"""

    }

}
