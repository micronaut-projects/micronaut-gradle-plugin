package io.micronaut.gradle.dev

import io.micronaut.gradle.fixtures.AbstractFunctionalTest
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.UnexpectedBuildFailure
import spock.lang.Requires
import spock.lang.Timeout

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * {@code mnDev} end to end: the application is launched, a controller is edited and another added while it runs, and
 * the new code answers from the same process, which is then stopped.
 *
 * <p>Development mode is in micronaut-dev 5.3: until it is released the launch needs core published to a local
 * repository, {@code ~/.m2/repository} or the one the {@code micronautDevRepository} system property names, and is
 * skipped without it.</p>
 */
@Requires({ !os.windows && MicronautDevReloadFunctionalTest.isDevModeAvailable() })
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class MicronautDevReloadFunctionalTest extends AbstractFunctionalTest {

    private static final String CORE_VERSION = System.getProperty("micronautDevCoreVersion", "5.3.0-SNAPSHOT")
    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(3)
    private static final Duration RELOAD_TIMEOUT = Duration.ofSeconds(60)

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
    private ProcessHandle launcher

    static File devRepository() {
        String repository = System.getProperty("micronautDevRepository")
        repository ? new File(repository) : new File(System.getProperty("user.home"), ".m2/repository")
    }

    static boolean isDevModeAvailable() {
        new File(devRepository(), "io/micronaut/micronaut-dev/$CORE_VERSION").isDirectory()
    }

    def cleanup() {
        // a failed run leaves nothing behind: the launcher of this project, which the build running in this JVM started,
        // whether or not the application answered
        String project = baseDir.fileName.toString()
        List<ProcessHandle> launchers = new ArrayList<>(ProcessHandle.current().descendants().filter { process ->
            process.info().arguments().map { arguments ->
                arguments.any { it.contains("MicronautDevMain") } && arguments.any { it.contains(project) }
            }.orElse(false)
        }.toList())
        if (launcher != null) {
            launchers << launcher
        }
        launchers.each { process ->
            process.descendants().forEach { it.destroyForcibly() }
            process.destroyForcibly()
        }
    }

    def "mnDev reloads an edited controller and a new one in the same process, and stops cleanly"() {
        given:
        int port = freePort()
        writeProject(port)
        writeController("HelloController", "/hello", "one")

        when: "mnDev runs in the background"
        long started = System.nanoTime()
        CompletableFuture<Object> run = CompletableFuture.supplyAsync {
            try {
                configureRunner("mnDev").run()
            } catch (UnexpectedBuildFailure failure) {
                failure.buildResult
            }
        }

        then: "the application answers with the controller as written"
        awaitBody(run, port, "/hello", "one", STARTUP_TIMEOUT)
        long pid = Long.parseLong(awaitAny(run, port, "/pid", RELOAD_TIMEOUT))
        ProcessHandle.of(pid).present
        println "mnDev answered after ${elapsedSeconds(started)}s"

        when: "the controller is edited"
        launcher = ProcessHandle.of(pid).get()
        long edited = System.nanoTime()
        writeController("HelloController", "/hello", "two")

        then: "the edit answers, from the same process"
        awaitBody(run, port, "/hello", "two", RELOAD_TIMEOUT)
        awaitAny(run, port, "/pid", RELOAD_TIMEOUT) == String.valueOf(pid)
        println "the edit answered after ${elapsedSeconds(edited)}s"

        when: "a controller is added"
        long added = System.nanoTime()
        writeController("AddedController", "/added", "added")

        then: "it answers, from the same process"
        awaitBody(run, port, "/added", "added", RELOAD_TIMEOUT)
        awaitAny(run, port, "/pid", RELOAD_TIMEOUT) == String.valueOf(pid)
        get(port, "/hello") == "two"
        println "the new controller answered after ${elapsedSeconds(added)}s"

        when: "the launcher is stopped"
        List<ProcessHandle> processes = [launcher] + launcher.descendants().toList()
        launcher.destroy()
        BuildResult result = run.get(60, TimeUnit.SECONDS) as BuildResult

        then: "it shuts the application down and leaves no process behind"
        launcher.onExit().get(30, TimeUnit.SECONDS) != null
        processes.every { !it.alive }
        !result.output.contains("Exception in thread")
        portIsFree(port)
        println "mnDev ran for ${elapsedSeconds(started)}s"
    }

    private void writeProject(int port) {
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.application"
            }
            group = "example"
            micronaut {
                version "$micronautVersion"
                runtime "netty"
                dev {
                    // no server on the default port, which another run may hold
                    liveReload {
                        enabled = false
                    }
                }
            }
            repositories {
                maven {
                    url = "${devRepository().toURI()}"
                    content { includeGroup "io.micronaut" }
                }
                mavenCentral()
            }
            // the application and the launcher both on the core that has development mode
            configurations.configureEach {
                resolutionStrategy.eachDependency {
                    if (requested.group == "io.micronaut") {
                        useVersion("$CORE_VERSION")
                    }
                }
            }
            dependencies {
                // the JSON the server answers errors with, from core
                runtimeOnly("io.micronaut:micronaut-jackson-databind")
                runtimeOnly("ch.qos.logback:logback-classic")
            }
            application { mainClass = "example.Application" }
        """
        writeSource("src/main/resources/application.properties", """
            micronaut.application.name=hello-world
            micronaut.server.port=$port
        """)
        writeSource("src/main/resources/logback.xml", """
            <configuration>
                <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
                    <encoder><pattern>%d{HH:mm:ss.SSS} %-5level %logger{36} - %msg%n</pattern></encoder>
                </appender>
                <root level="info"><appender-ref ref="STDOUT"/></root>
            </configuration>
        """)
        writeSource("src/main/java/example/Application.java", """
            package example;
            import io.micronaut.runtime.Micronaut;
            public class Application {
                public static void main(String[] args) {
                    Micronaut.run(Application.class, args);
                }
            }
        """)
        writeSource("src/main/java/example/PidController.java", """
            package example;
            import io.micronaut.http.MediaType;
            import io.micronaut.http.annotation.Controller;
            import io.micronaut.http.annotation.Get;
            import io.micronaut.http.annotation.Produces;
            @Controller("/pid")
            public class PidController {
                @Get
                @Produces(MediaType.TEXT_PLAIN)
                public String pid() {
                    return String.valueOf(ProcessHandle.current().pid());
                }
            }
        """)
    }

    private void writeController(String name, String path, String body) {
        File source = file("src/main/java/example/${name}.java")
        long previous = source.exists() ? source.lastModified() : 0
        writeSource(source, """
            package example;
            import io.micronaut.http.MediaType;
            import io.micronaut.http.annotation.Controller;
            import io.micronaut.http.annotation.Get;
            import io.micronaut.http.annotation.Produces;
            @Controller("$path")
            public class $name {
                @Get
                @Produces(MediaType.TEXT_PLAIN)
                public String index() {
                    return "$body";
                }
            }
        """)
        // a watcher comparing modification times sees the edit even within the file system's resolution
        if (source.lastModified() <= previous) {
            source.setLastModified(previous + 2000)
        }
    }

    private String awaitBody(CompletableFuture<Object> run, int port, String path, String expected, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos()
        String last = null
        while (System.nanoTime() < deadline) {
            assertRunning(run)
            last = get(port, path)
            if (last == expected) {
                return last
            }
            Thread.sleep(250)
        }
        throw new AssertionError("$path did not answer '$expected' within $timeout: last answer '$last'")
    }

    private String awaitAny(CompletableFuture<Object> run, int port, String path, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < deadline) {
            assertRunning(run)
            String body = get(port, path)
            if (body != null) {
                return body
            }
            Thread.sleep(250)
        }
        throw new AssertionError("$path did not answer within $timeout")
    }

    private static void assertRunning(CompletableFuture<Object> run) {
        if (run.done) {
            Object result = run.get()
            throw new AssertionError("mnDev ended before the application answered:\n" + (result instanceof BuildResult ? result.output : result))
        }
    }

    private String get(int port, String path) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).timeout(Duration.ofSeconds(5)).build()
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString())
            response.statusCode() == 200 ? response.body() : null
        } catch (IOException ignored) {
            null
        }
    }

    private static int freePort() {
        new ServerSocket(0).withCloseable { it.localPort }
    }

    private static boolean portIsFree(int port) {
        try {
            new ServerSocket(port).close()
            true
        } catch (IOException ignored) {
            false
        }
    }

    private static String elapsedSeconds(long since) {
        String.format("%.1f", (System.nanoTime() - since) / 1e9d)
    }

    private void writeSource(String path, String source) {
        writeSource(file(path), source)
    }

    private static void writeSource(File file, String source) {
        file.parentFile.mkdirs()
        file.text = source.stripIndent()
    }
}
