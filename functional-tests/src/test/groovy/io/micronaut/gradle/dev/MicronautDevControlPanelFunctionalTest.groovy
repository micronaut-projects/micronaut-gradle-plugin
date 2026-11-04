package io.micronaut.gradle.dev

import io.micronaut.gradle.fixtures.AbstractEagerConfiguringFunctionalTest
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.UnexpectedBuildFailure
import spock.lang.Requires
import spock.lang.Timeout

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class MicronautDevControlPanelFunctionalTest extends AbstractEagerConfiguringFunctionalTest {

    // Control Panel requires Micronaut Platform 5.2.4 or later, which manages Control Panel 2.2.1
    private static final String PLATFORM_VERSION = "5.2.4"

    def "mnDev adds Micronaut Control Panel with its development defaults, and the production classpath is unchanged"() {
        given:
        application()

        when:
        def result = build("mnDevManifest", "mnDevControlPanelDefaults", "dependencies", "--configuration", "runtimeClasspath")

        then:
        result.task(":mnDevManifest").outcome == TaskOutcome.SUCCESS
        def runtime = runtimeClasspath()
        runtime.any { it.contains("micronaut-control-panel-ui-2.2.1.jar") }
        runtime.any { it.contains("micronaut-control-panel-management-2.2.1.jar") }
        runtime.every { !it.contains("micronaut-control-panel-kafka-") }
        !result.output.contains("Micronaut Control Panel requires Micronaut Platform")
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

    def "Control Panel is not added on a Micronaut Platform older than 5.2.4"() {
        given:
        application("", "", "5.2.3")

        when:
        def result = build("mnDevManifest", "dependencies", "--configuration", "mnDevControlPanel")

        then:
        result.task(":mnDevManifest").outcome == TaskOutcome.SUCCESS
        result.output.count("Micronaut Control Panel requires Micronaut Platform 5.2.4 or later; not adding it (the build uses 5.2.3)") == 1
        runtimeClasspath().every { !it.contains("micronaut-control-panel") }
        result.output.contains("No dependencies")
        !defaultsFile().exists()
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

    @Requires({ !os.windows })
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    def "mnDev serves Control Panel behind the access token of the launch"() {
        given:
        int port
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.localPort
        }
        application('runtimeOnly("ch.qos.logback:logback-classic"); runtimeOnly("io.micronaut:micronaut-jackson-databind")', """
            dev {
                liveReload {
                    enabled = false
                }
            }
        """)
        // the application on the core that has development mode, with what the platform gives it
        buildFile << """
            $DEV_CORE_REPOSITORY
            configurations.configureEach {
                resolutionStrategy.eachDependency {
                    if (requested.group == "io.micronaut") {
                        useVersion("$DEV_CORE_VERSION")
                    }
                }
            }
        """
        file("src/main/resources").mkdirs()
        file("src/main/resources/application.properties").text = "micronaut.server.port=$port\n"
        file("src/main/java/example/Application.java").text = """
            package example;
            import io.micronaut.runtime.Micronaut;
            public class Application {
                public static void main(String[] args) {
                    Micronaut.run(Application.class, args);
                }
            }
        """.stripIndent()
        def output = new StringWriter()
        def forwarded = new Writer() {
            void write(char[] chars, int off, int len) { synchronized (output) { output.write(chars, off, len) } }
            void flush() { }
            void close() { }
        }
        def text = { synchronized (output) { return output.toString() } }

        when:
        def run = CompletableFuture.supplyAsync {
            try {
                configureRunner("mnDev").forwardStdOutput(forwarded).run()
            } catch (UnexpectedBuildFailure failure) {
                failure.buildResult
            }
        }
        def link = null
        long deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(3)
        while (link == null && !run.done && System.currentTimeMillis() < deadline) {
            def matcher = text() =~ /Development pages: (\S+)\?token=(\S+)/
            link = matcher.find() ? [matcher.group(1), matcher.group(2)] : null
            Thread.sleep(250)
        }
        if (link == null) {
            throw new AssertionError("mnDev logged no link to the development pages:\n" + text())
        }

        then: "the startup note links to Control Panel, on localhost, with the token"
        link[0] == "http://localhost:$port/control-panel".toString()

        and: "the panels answer the token, and nothing else"
        status("http://localhost:$port/control-panel", [:]) == 401
        status("http://localhost:$port/control-panel", ["X-Micronaut-Dev-Token": link[1]]) == 200
        status("http://127.0.0.1:$port/control-panel", ["X-Micronaut-Dev-Token": link[1]]) == 200

        cleanup:
        ProcessHandle.current().descendants().filter { process ->
            process.info().arguments().map { arguments ->
                arguments.any { it.contains("MicronautDevMain") } && arguments.any { it.contains(baseDir.fileName.toString()) }
            }.orElse(false)
        }.forEach { it.destroyForcibly() }
        run?.get(60, TimeUnit.SECONDS)
    }

    private void application(String dependencies = "", String dev = "", String platformVersion = PLATFORM_VERSION) {
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.application"
            }
            micronaut {
                version "$platformVersion"
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

    private static int status(String url, Map<String, String> headers) {
        def builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
        headers.each { name, value -> builder.header(name, value) }
        HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.discarding()).statusCode()
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
