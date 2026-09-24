package io.micronaut.gradle.docker

import io.micronaut.gradle.AbstractGradleBuildSpec
import io.micronaut.gradle.fixtures.AbstractFunctionalTest
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf
import spock.lang.Issue

import java.util.concurrent.TimeUnit

@Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/1379")
@IgnoreIf({ os.windows || !AbstractGradleBuildSpec.dockerAvailable })
class JdkAotCacheDockerFunctionalTest extends AbstractFunctionalTest {

    private static final String CACHE = "/home/app/application.aot"

    private final String id = UUID.randomUUID().toString()
    private final String image = "micronaut-gradle-jdk-aot-cache-$id"

    def cleanup() {
        docker("rmi", "-f", "$image:main", "$image:optimized")
        // A failed image build leaves the container of the failed step behind
        def containers = docker("ps", "-a", "--no-trunc", "--filter", "status=exited", "--format", "{{.ID}} {{.Command}}").output
            .readLines()
            .findAll { it.contains(id) }
            .collect { it.split(" ")[0] }
        if (containers) {
            docker("rm", *containers)
        }
    }

    def "the main image trains a JDK AOT cache that serves application and dependency classes"() {
        given:
        withApplication(["/hello", "/hello?name=training"])

        when:
        def result = build("dockerBuild")

        then:
        result.task(":dockerBuild").outcome == TaskOutcome.SUCCESS
        result.output.contains("[jdk-aot-cache] GET /hello: 200")
        result.output.contains("[jdk-aot-cache] GET /hello?name=training: 200")
        result.output.contains("[jdk-aot-cache] Wrote $CACHE")

        when: "the application starts in strict mode"
        def strict = startApplication("$image:main", "-XX:AOTMode=on", "-XX:AOTCache=$CACHE", "-XX:+UseG1GC", "-Xlog:class+load=info")

        then:
        strict.exitCode == 0
        strict.output.contains("example.HelloController source: shared objects file")
        strict.output.contains("io.micronaut.http.server.netty.NettyHttpServer source: shared objects file")

        when: "the application starts in auto mode with another collector"
        def serial = startApplication("$image:main", "-XX:AOTCache=$CACHE", "-XX:+UseSerialGC")

        then:
        serial.exitCode == 0
        serial.output.contains("Startup completed")
    }

    def "a failed training fails the image build"() {
        given:
        withApplication(["/hello", "/missing-$id"])

        when:
        def result = fails("dockerBuild")

        then:
        result.output.contains("[jdk-aot-cache] GET /hello: 200")
        result.output.contains("[jdk-aot-cache] ERROR: GET /missing-$id answered with status 404")
    }

    def "the optimized image trains a JDK AOT cache that serves application and dependency classes"() {
        given:
        withApplication(["/hello"], 'id "io.micronaut.aot"')

        when:
        def result = build("optimizedDockerBuild")

        then:
        result.task(":optimizedDockerBuild").outcome == TaskOutcome.SUCCESS
        result.output.contains("[jdk-aot-cache] GET /hello: 200")

        when:
        def strict = startApplication("$image:optimized", "-XX:AOTMode=on", "-XX:AOTCache=$CACHE", "-XX:+UseG1GC", "-Xlog:class+load=info")

        then:
        strict.exitCode == 0
        strict.output.contains("example.HelloController source: shared objects file")
        strict.output.contains("io.micronaut.http.server.netty.NettyHttpServer source: shared objects file")
    }

    private void withApplication(List<String> trainingPaths, String extraPlugin = "") {
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.docker"
                $extraPlugin
            }

            micronaut {
                version "$micronautVersion"
                runtime "netty"
                docker {
                    jdkAotCache {
                        enabled = true
                        trainingPaths = [${trainingPaths.collect { "'$it'" }.join(", ")}]
                        strictProbes = 3
                    }
                }
            }

            $repositoriesBlock

            application { mainClass = "example.Application" }

            dependencies {
                runtimeOnly("ch.qos.logback:logback-classic")
                runtimeOnly("io.micronaut.serde:micronaut-serde-jackson")
            }

            tasks.withType(com.bmuschko.gradle.docker.tasks.image.DockerBuildImage).configureEach {
                images = ["$image:\${name == 'dockerBuild' ? 'main' : 'optimized'}"]
            }
        """
        file("src/main/java/example/Application.java").tap {
            parentFile.mkdirs()
            text = """
package example;

import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.Micronaut;

public class Application {
    public static void main(String... args) {
        ApplicationContext context = Micronaut.run(Application.class, args);
        if (System.getenv("EXIT_AFTER_STARTUP") != null) {
            context.close();
            System.exit(0);
        }
    }
}
"""
        }
        file("src/main/resources/logback.xml").tap {
            parentFile.mkdirs()
            text = """<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder><pattern>%-5level %logger{36} - %msg%n</pattern></encoder>
    </appender>
    <root level="info"><appender-ref ref="STDOUT"/></root>
</configuration>
"""
        }
        file("src/main/java/example/HelloController.java").text = """
package example;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;

@Controller("/hello")
public class HelloController {
    @Get
    public String hello(@QueryValue(defaultValue = "World") String name) {
        return "Hello " + name;
    }
}
"""
    }

    /**
     * Starts the application of an image with the given JVM options, and stops it once it has started.
     */
    private static ProcessResult startApplication(String image, String... jvmOptions) {
        docker("run", "--rm", "-e", "EXIT_AFTER_STARTUP=true", "--entrypoint", "java", image, *jvmOptions, "-jar", "/home/app/application.jar")
    }

    private static ProcessResult docker(String... args) {
        def process = new ProcessBuilder(["docker", *args])
            .redirectErrorStream(true)
            .start()
        def output = new StringBuilder()
        def reader = Thread.start { output << process.inputStream.text }
        if (!process.waitFor(3, TimeUnit.MINUTES)) {
            process.destroyForcibly().waitFor()
        }
        reader.join()
        new ProcessResult(process.exitValue(), output.toString())
    }

    private static class ProcessResult {
        final int exitCode
        final String output

        ProcessResult(int exitCode, String output) {
            this.exitCode = exitCode
            this.output = output
        }
    }
}
