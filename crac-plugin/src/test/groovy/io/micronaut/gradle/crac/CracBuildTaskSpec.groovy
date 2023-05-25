package io.micronaut.gradle.crac

import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf

@IgnoreIf({ os.windows })
@IgnoreIf(value = { os.macOs && System.properties['os.arch'] == 'aarch64' }, reason = "Java 11 compatible Docker not supported on OSX M1 architecture")
class CracBuildTaskSpec extends BaseCracGradleBuildSpec {

    def "test build docker image when #desc"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << getBuildFileBlock(switchPluginOrder)

        writeJavaFile("src/main/java/example/Application.java", """package example;

import io.micronaut.runtime.Micronaut;

class Application {
    public static void main(String... args) {
        Micronaut.run(Application.class, args);
    }
}
""")
        writeJavaFile("src/main/java/example/HelloController.java", """package example;

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
}""")

        writeXmlFile("src/main/resources/logback.xml", """
<configuration>
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
</configuration>""")

        writeYamlFile("src/main/resources/application.yml", """
micronaut:
  application:
    name: demo
netty:
  default:
    allocator:
      max-order: 3
""")

        when:
        def result = build('dockerBuildCrac', '-s')
        def task = result.task(":dockerBuildCrac")

        then:
        result.output.contains("Successfully tagged hello-world:latest")
        task.outcome == TaskOutcome.SUCCESS

        where:
        desc                   | switchPluginOrder
        'crac plugin is first' | true
        'crac plugin is last'  | false
    }
}
