package io.micronaut.gradle

import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf

@IgnoreIf({ os.windows })
class CracBuildTaskSpec extends AbstractGradleBuildSpec {

    def "test build docker image"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.docker"
                id "io.micronaut.crac"
            }

            repositories {
                mavenLocal()
                mavenCentral()
                maven { url = "https://s01.oss.sonatype.org/content/repositories/snapshots" }
            }
            
            dependencies {
                implementation("io.micronaut.crac:micronaut-crac:1.0.0-SNAPSHOT")
            }

            micronaut {
                version "3.6.1"
            }

            micronaut {
                runtime("netty")
                testRuntime("junit5")
                processing {
                    incremental(true)
                    annotations("example.*")
                }
            }

            mainClassName="example.Application"
            
        """
        testProjectDir.newFolder("src", "main", "java", "example")
        def javaFile = testProjectDir.newFile("src/main/java/example/Application.java")
        javaFile.parentFile.mkdirs()
        javaFile << """
package example;

import io.micronaut.runtime.Micronaut;

class Application {
    public static void main(String... args) {
        Micronaut.run(Application.class, args);
    }
}
"""
        testProjectDir.newFile("src/main/java/example/HelloController.java").with {
            parentFile.mkdirs()
            it << """package example;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;

import java.util.Optional;

@Controller
public class HelloController {

    @Get("/")
    public String hello(@Nullable String name) {
        return "Hello";
    }
}"""
        }
        testProjectDir.newFolder("src", "main", "resources")
        testProjectDir.newFile("src/main/resources/logback.xml").with {
            parentFile.mkdirs()
            it << """<configuration>

    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <withJansi>true</withJansi>
        <!-- encoders are assigned the type
             ch.qos.logback.classic.encoder.PatternLayoutEncoder by default -->
        <encoder>
            <pattern>%cyan(%d{HH:mm:ss.SSS}) %gray([%thread]) %highlight(%-5level) %magenta(%logger{36}) - %msg%n</pattern>
        </encoder>
    </appender>

    <root level="info">
        <appender-ref ref="STDOUT" />
    </root>
    <logger name="io.micronaut.crac" level="debug"/>
</configuration>"""
        }
        testProjectDir.newFile("src/main/resources/application.yml").with {
            parentFile.mkdirs()
            it << """micronaut:
  application:
    name: demo
netty:
  default:
    allocator:
      max-order: 3
"""
        }

        when:
        def result = build('dockerBuildCrac', '-s')

        def task = result.task(":dockerBuildCrac")
        then:
        result.output.contains("Successfully tagged hello-world:latest")
        task.outcome == TaskOutcome.SUCCESS
    }
}
