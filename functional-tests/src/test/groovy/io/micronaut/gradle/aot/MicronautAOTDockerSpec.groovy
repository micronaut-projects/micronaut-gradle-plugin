package io.micronaut.gradle.aot

import io.micronaut.gradle.AbstractGradleBuildSpec
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf
import spock.lang.Requires

@IgnoreIf({ os.windows })
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

    @Requires({ AbstractGradleBuildSpec.graalVmAvailable && !os.windows })
    def "generates a native optimized docker image"() {
        withSample("aot/basic-app")

        when:
        def result = build "optimizedDockerBuildNative"

        then:
        result.task(":prepareNativeOptimizations").outcome == TaskOutcome.SUCCESS
        result.task(":optimizedDockerfileNative").outcome == TaskOutcome.SUCCESS
        result.task(":optimizedNativeJar").outcome == TaskOutcome.SUCCESS
        result.task(":optimizedRunnerNativeJar").outcome == TaskOutcome.SUCCESS
        result.task(":optimizedBuildNativeLayersTask").outcome == TaskOutcome.SUCCESS
        result.task(":optimizedDockerfileNative").outcome == TaskOutcome.SUCCESS
        result.task(":optimizedDockerBuildNative").outcome == TaskOutcome.SUCCESS
        result.tasks.stream().noneMatch { it.path == ":nativeCompile" }

        def dockerFile = normalizeLineEndings(file("build/docker/native-optimized/DockerfileNative").text)
        dockerFile == """FROM ghcr.io/graalvm/native-image:ol8-java17-22.3.2 AS graalvm
WORKDIR /home/app
COPY layers/libs /home/app/libs
COPY layers/classes /home/app/classes
COPY layers/resources /home/app/resources
COPY layers/application.jar /home/app/application.jar
RUN mkdir /home/app/config-dirs
RUN mkdir -p /home/app/config-dirs/generateResourcesConfigFile
RUN mkdir -p /home/app/config-dirs/io.netty/netty-common/4.1.80.Final
RUN mkdir -p /home/app/config-dirs/ch.qos.logback/logback-classic/1.4.1
COPY config-dirs/generateResourcesConfigFile /home/app/config-dirs/generateResourcesConfigFile
COPY config-dirs/io.netty/netty-common/4.1.80.Final /home/app/config-dirs/io.netty/netty-common/4.1.80.Final
COPY config-dirs/ch.qos.logback/logback-classic/1.4.1 /home/app/config-dirs/ch.qos.logback/logback-classic/1.4.1
RUN native-image --exclude-config .*/libs/netty-codec-http-4.1.93.Final.jar ^/META-INF/native-image/.* --exclude-config .*/libs/netty-transport-4.1.93.Final.jar ^/META-INF/native-image/.* --exclude-config .*/libs/netty-handler-4.1.93.Final.jar ^/META-INF/native-image/.* --exclude-config .*/libs/netty-codec-http2-4.1.93.Final.jar ^/META-INF/native-image/.* --exclude-config .*/libs/netty-common-4.1.93.Final.jar ^/META-INF/native-image/.* --exclude-config .*/libs/netty-buffer-4.1.93.Final.jar ^/META-INF/native-image/.* -cp /home/app/libs/*.jar:/home/app/resources:/home/app/application.jar --no-fallback -H:Name=application -H:ConfigurationFileDirectories=/home/app/config-dirs/generateResourcesConfigFile,/home/app/config-dirs/io.netty/netty-buffer/4.1.80.Final,/home/app/config-dirs/io.netty/netty-common/4.1.80.Final,/home/app/config-dirs/io.netty/netty-codec-http/4.1.80.Final,/home/app/config-dirs/io.netty/netty-transport/4.1.80.Final,/home/app/config-dirs/io.netty/netty-handler/4.1.80.Final,/home/app/config-dirs/io.netty/netty-codec-http2/4.1.80.Final,/home/app/config-dirs/ch.qos.logback/logback-classic/1.4.1 -H:Class=demo.app.Application
FROM frolvlad/alpine-glibc:alpine-3.12
RUN apk --no-cache update && apk add libstdc++
EXPOSE 8080
COPY --from=graalvm /home/app/application /app/application
ENTRYPOINT ["/app/application"]
"""

    }

}
