package io.micronaut.gradle.dev

import io.micronaut.gradle.fixtures.AbstractEagerConfiguringFunctionalTest
import org.gradle.testkit.runner.TaskOutcome

class MicronautDevControlPanelFunctionalTest extends AbstractEagerConfiguringFunctionalTest {

    def "mnDev adds Micronaut Control Panel with its development defaults, and the production classpath is unchanged"() {
        given:
        application()

        when:
        def result = build("mnDevManifest", "mnDevControlPanelDefaults", "dependencies", "--configuration", "runtimeClasspath")

        then:
        result.task(":mnDevManifest").outcome == TaskOutcome.SUCCESS
        def runtime = runtimeClasspath()
        runtime.any { it.contains("micronaut-control-panel-ui-") }
        runtime.any { it.contains("micronaut-control-panel-management-") }
        runtime.every { !it.contains("micronaut-control-panel-kafka-") }
        runtime.any { it.endsWith("micronaut-dev" + File.separator + "control-panel") }

        and: "the defaults are the property source Control Panel reads with the lowest precedence"
        def defaults = defaults()
        defaults."micronaut.control-panel.enabled" == "true"
        defaults."micronaut.control-panel.path" == "/control-panel"
        defaults."micronaut.control-panel.security.access" == "ANONYMOUS"

        and: "the launcher is told to bind the application to localhost by default"
        manifest()."micronaut.dev.bind-loopback" == "true"

        and: "the runtime classpath of production has no Control Panel"
        !result.output.contains("micronaut-control-panel-management")
        !result.output.contains("micronaut-control-panel-ui")

        when: "the configuration cache stores the manifest task and reuses it"
        file("build/micronaut-dev").deleteDir()
        def stored = build("mnDevManifest", "--configuration-cache")
        def reused = build("mnDevManifest", "--configuration-cache")

        then:
        stored.output.contains("Configuration cache entry stored")
        reused.output.contains("Reusing configuration cache")
        runtimeClasspath().any { it.contains("micronaut-control-panel-management-") }
    }

    def "the Kafka panel is added for an application that uses Micronaut Kafka"() {
        given:
        application('implementation("io.micronaut.kafka:micronaut-kafka")')

        when:
        def result = build("mnDevManifest", "dependencies", "--configuration", "mnDevApplicationClasspath")

        then:
        result.task(":mnDevManifest").outcome == TaskOutcome.SUCCESS
        def runtime = runtimeClasspath()
        runtime.any { it.contains("micronaut-control-panel-kafka-") }
        runtime.any { it.contains("micronaut-control-panel-management-") }
        runtime.every { !it.contains("micronaut-control-panel-datasource-") }
        result.output.contains("io.micronaut.controlpanel:micronaut-control-panel-kafka")
    }

    def "Control Panel is turned off with the DSL or the Gradle property, which wins"() {
        given:
        application("", """
            dev {
                controlPanel {
                    enabled = false
                }
            }
        """)

        when:
        def result = build("mnDevManifest")

        then:
        result.task(":mnDevManifest").outcome == TaskOutcome.SUCCESS
        runtimeClasspath().every { !it.contains("micronaut-control-panel") }
        !defaultsFile().exists()

        when: "the Gradle property turns it on over the DSL"
        build("mnDevManifest", "-Pmicronaut.dev.control-panel=true")

        then:
        runtimeClasspath().any { it.contains("micronaut-control-panel-management-") }
        defaultsFile().exists()

        when: "and off, whatever the DSL says"
        buildFile.text = buildFile.text.replace("enabled = false", "enabled = true")
        build("mnDevManifest", "-Pmicronaut.dev.control-panel=false")

        then:
        runtimeClasspath().every { !it.contains("micronaut-control-panel") }
        !defaultsFile().exists()
    }

    def "with Micronaut Security the defaults do not open Control Panel to anonymous users"() {
        given:
        application('implementation("io.micronaut.security:micronaut-security")')

        when:
        def result = build("mnDevManifest")

        then:
        result.task(":mnDevManifest").outcome == TaskOutcome.SUCCESS
        def defaults = defaults()
        defaults."micronaut.control-panel.enabled" == "true"
        defaults."micronaut.control-panel.path" == "/control-panel"
        !defaults.containsKey("micronaut.control-panel.security.access")
    }

    def "binding to localhost is turned off with the DSL or the Gradle property, which wins"() {
        given:
        application("", """
            dev {
                bindLoopback = false
            }
        """)

        when:
        build("mnDevManifest")

        then:
        manifest()."micronaut.dev.bind-loopback" == "false"

        when: "the Gradle property turns it on over the DSL"
        build("mnDevManifest", "-Pmicronaut.dev.bind-loopback=true")

        then:
        manifest()."micronaut.dev.bind-loopback" == "true"

        when: "and off, whatever the DSL says"
        buildFile.text = buildFile.text.replace("bindLoopback = false", "bindLoopback = true")
        build("mnDevManifest", "-Pmicronaut.dev.bind-loopback=false")

        then:
        manifest()."micronaut.dev.bind-loopback" == "false"
    }

    private void application(String dependencies = "", String dev = "") {
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.application"
            }
            micronaut {
                version "$micronautVersion"
                runtime "netty"
                $dev
            }
            $repositoriesBlock
            dependencies {
                $dependencies
            }
            application { mainClass = "example.Application" }
        """
        def source = file("src/main/java/example/Application.java")
        source.parentFile.mkdirs()
        source << """
            package example;
            public class Application {
                public static void main(String[] args) { }
            }
        """.stripIndent()
    }

    private List<String> runtimeClasspath() {
        file("build/micronaut-dev/runtime.argfile").readLines()
    }

    private Properties defaults() {
        Properties properties = new Properties()
        defaultsFile().withInputStream { properties.load(it) }
        properties
    }

    private Properties manifest() {
        Properties properties = new Properties()
        file("build/micronaut-dev/dev.properties").withInputStream { properties.load(it) }
        properties
    }

    private File defaultsFile() {
        file("build/micronaut-dev/control-panel/micronaut-control-panel/micronaut-control-panel.properties")
    }
}
