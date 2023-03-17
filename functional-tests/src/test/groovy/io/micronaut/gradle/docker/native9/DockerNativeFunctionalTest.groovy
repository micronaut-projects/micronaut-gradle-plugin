package io.micronaut.gradle.docker.native9

import io.micronaut.gradle.AbstractGradleBuildSpec
import io.micronaut.gradle.fixtures.AbstractEagerConfiguringFunctionalTest
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Requires

@Requires({ AbstractGradleBuildSpec.graalVmAvailable })
@IgnoreIf({ os.windows })
class DockerNativeFunctionalTest extends AbstractEagerConfiguringFunctionalTest {

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


        def result = build('dockerBuildNative')

        def task = result.task(":dockerBuildNative")
        def dockerFile = new File(testProjectDir.root, 'build/docker/native-main/DockerfileNative').text.trim()

        expect:
        task.outcome == TaskOutcome.SUCCESS
        dockerFile == """
FROM ghcr.io/graalvm/native-image:ol7-java17-22.3.0 AS graalvm
WORKDIR /home/alternate
COPY layers/libs /home/alternate/libs
COPY layers/classes /home/alternate/classes
COPY layers/resources /home/alternate/resources
COPY layers/application.jar /home/alternate/application.jar
RUN mkdir /home/alternate/config-dirs
RUN mkdir -p /home/alternate/config-dirs/generateResourcesConfigFile
RUN mkdir -p /home/alternate/config-dirs/io.netty/netty-common/4.1.80.Final
COPY config-dirs/generateResourcesConfigFile /home/alternate/config-dirs/generateResourcesConfigFile
COPY config-dirs/io.netty/netty-common/4.1.80.Final /home/alternate/config-dirs/io.netty/netty-common/4.1.80.Final
RUN native-image --exclude-config .*/libs/netty-buffer-4.1.87.Final.jar ^/META-INF/native-image/.* --exclude-config .*/libs/netty-transport-4.1.87.Final.jar ^/META-INF/native-image/.* --exclude-config .*/libs/netty-codec-http2-4.1.87.Final.jar ^/META-INF/native-image/.* --exclude-config .*/libs/netty-common-4.1.87.Final.jar ^/META-INF/native-image/.* --exclude-config .*/libs/netty-codec-http-4.1.87.Final.jar ^/META-INF/native-image/.* --exclude-config .*/libs/netty-handler-4.1.87.Final.jar ^/META-INF/native-image/.* -cp /home/alternate/libs/*.jar:/home/alternate/resources:/home/alternate/application.jar --no-fallback -H:Name=application -J--add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.core.configure=ALL-UNNAMED -J--add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.core.jdk=ALL-UNNAMED -J--add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.core.jni=ALL-UNNAMED -J--add-exports=org.graalvm.sdk/org.graalvm.nativeimage.impl=ALL-UNNAMED -H:ConfigurationFileDirectories=/home/alternate/config-dirs/generateResourcesConfigFile,/home/alternate/config-dirs/io.netty/netty-buffer/4.1.80.Final,/home/alternate/config-dirs/io.netty/netty-common/4.1.80.Final,/home/alternate/config-dirs/io.netty/netty-codec-http/4.1.80.Final,/home/alternate/config-dirs/io.netty/netty-transport/4.1.80.Final,/home/alternate/config-dirs/io.netty/netty-handler/4.1.80.Final,/home/alternate/config-dirs/io.netty/netty-codec-http2/4.1.80.Final -H:Class=example.Application
FROM frolvlad/alpine-glibc:alpine-3.12
RUN apk --no-cache update && apk add libstdc++
EXPOSE 8080
HEALTHCHECK CMD curl -s localhost:8090/health | grep '"status":"UP"'
COPY --from=graalvm /home/alternate/application /app/application
ENTRYPOINT ["/app/application", "-Xmx64m"]
""".trim()

    }

}
