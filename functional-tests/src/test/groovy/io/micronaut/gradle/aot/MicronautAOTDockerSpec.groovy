package io.micronaut.gradle.aot

import io.micronaut.gradle.AbstractGradleBuildSpec
import io.micronaut.gradle.DefaultVersions
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
        result.task(":optimizedDockerfile").outcome != TaskOutcome.FAILED

        def dockerFile = normalizeLineEndings(file("build/docker/optimized/Dockerfile").text)
        dockerFile == """FROM eclipse-temurin:17-jre-focal
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
        result.task(":prepareJitOptimizations").outcome != TaskOutcome.FAILED
        result.task(":optimizedDockerfile").outcome != TaskOutcome.FAILED
        result.task(":optimizedJitJar").outcome != TaskOutcome.FAILED
        result.task(":optimizedRunnerJitJar").outcome != TaskOutcome.FAILED
        result.task(":optimizedBuildLayers").outcome != TaskOutcome.FAILED
        result.task(":optimizedDockerBuild").outcome != TaskOutcome.FAILED

        def dockerFile = normalizeLineEndings(file("build/docker/optimized/Dockerfile").text)
        dockerFile == """FROM eclipse-temurin:17-jre-focal
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
        build "optimizedDockerfileNative"
        def dockerFile = normalizeLineEndings(file("build/docker/native-optimized/DockerfileNative").text)
        dockerFile = dockerFile.replaceAll("[0-9]\\.[0-9]+\\.[0-9]+", "4.0.0")

        then:
        dockerFile == """FROM ghcr.io/graalvm/native-image-community:17-ol${DefaultVersions.ORACLELINUX} AS graalvm
WORKDIR /home/app
COPY layers/libs /home/app/libs
COPY layers/classes /home/app/classes
COPY layers/resources /home/app/resources
COPY layers/application.jar /home/app/application.jar
RUN mkdir /home/app/config-dirs
RUN mkdir -p /home/app/config-dirs/generateResourcesConfigFile
RUN mkdir -p /home/app/config-dirs/io.netty/netty-common/4.0.0.Final
RUN mkdir -p /home/app/config-dirs/ch.qos.logback/logback-classic/4.0.0
COPY config-dirs/generateResourcesConfigFile /home/app/config-dirs/generateResourcesConfigFile
COPY config-dirs/io.netty/netty-common/4.0.0.Final /home/app/config-dirs/io.netty/netty-common/4.0.0.Final
COPY config-dirs/ch.qos.logback/logback-classic/4.0.0 /home/app/config-dirs/ch.qos.logback/logback-classic/4.0.0
RUN native-image --exclude-config .*/libs/netty-transport-4.0.0.Final.jar ^/META-INF/native-image/.* --exclude-config .*/libs/netty-buffer-4.0.0.Final.jar ^/META-INF/native-image/.* --exclude-config .*/libs/netty-codec-http-4.0.0.Final.jar ^/META-INF/native-image/.* --exclude-config .*/libs/netty-handler-4.0.0.Final.jar ^/META-INF/native-image/.* --exclude-config .*/libs/netty-common-4.0.0.Final.jar ^/META-INF/native-image/.* --exclude-config .*/libs/netty-codec-http2-4.0.0.Final.jar ^/META-INF/native-image/.* -cp /home/app/libs/*.jar:/home/app/resources:/home/app/application.jar --no-fallback -o application -H:ConfigurationFileDirectories=/home/app/config-dirs/generateResourcesConfigFile,/home/app/config-dirs/io.netty/netty-buffer/4.0.0.Final,/home/app/config-dirs/io.netty/netty-common/4.0.0.Final,/home/app/config-dirs/io.netty/netty-codec-http/4.0.0.Final,/home/app/config-dirs/io.netty/netty-transport/4.0.0.Final,/home/app/config-dirs/io.netty/netty-handler/4.0.0.Final,/home/app/config-dirs/io.netty/netty-codec-http2/4.0.0.Final,/home/app/config-dirs/ch.qos.logback/logback-classic/4.0.0 demo.app.Application
FROM frolvlad/alpine-glibc:alpine-${DefaultVersions.ALPINE}
RUN apk --no-cache update && apk add libstdc++
EXPOSE 8080
COPY --from=graalvm /home/app/application /app/application
ENTRYPOINT ["/app/application"]
"""

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
