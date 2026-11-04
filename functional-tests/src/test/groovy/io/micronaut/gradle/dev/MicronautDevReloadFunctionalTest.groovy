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
 * <p>Development mode is in micronaut-dev 5.3: until it is released the launch resolves core from the snapshots
 * repository.</p>
 */
@Requires({ !os.windows })
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class MicronautDevReloadFunctionalTest extends AbstractFunctionalTest {

    // the Kotlin the launcher compiles with, the Build Tools API it uses being of Kotlin 2.4
    private static final String EMBEDDED_KOTLIN_VERSION = "2.4.10"
    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(3)
    private static final Duration RELOAD_TIMEOUT = Duration.ofSeconds(60)

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
    private ProcessHandle launcher

    def cleanup() {
        // a failed run leaves nothing behind: the launcher of this project, which the build running in this JVM started,
        // whether or not the application answered
        List<ProcessHandle> launchers = launchers()
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
        !result.output.contains("A restricted method")
        result.output.contains("Development Mode Active")
        result.output.contains("Development pages: http://localhost:$port/dev?token=")
        portIsFree(port)
        println "mnDev ran for ${elapsedSeconds(started)}s"
    }

    def "mnDev starts the launcher again when a system property of the build gives it a generation budget it spends"() {
        given:
        int port = freePort()
        writeProject(port)
        writeController("HelloController", "/hello", "one")

        when: "mnDev runs with a budget of two generations"
        CompletableFuture<Object> run = CompletableFuture.supplyAsync {
            try {
                configureRunner("mnDev", "-Dmicronaut.dev.max-generations=2").run()
            } catch (UnexpectedBuildFailure failure) {
                failure.buildResult
            }
        }

        then:
        awaitBody(run, port, "/hello", "one", STARTUP_TIMEOUT)
        long first = Long.parseLong(awaitAny(run, port, "/pid", RELOAD_TIMEOUT))

        when: "a new class starts the second generation"
        writeController("SecondController", "/second", "second")

        then: "from the same process"
        awaitBody(run, port, "/second", "second", RELOAD_TIMEOUT)
        awaitAny(run, port, "/pid", RELOAD_TIMEOUT) == String.valueOf(first)

        when: "another would start a third"
        writeController("ThirdController", "/third", "third")

        then: "a new process serves it, and the build goes on"
        awaitBody(run, port, "/third", "third", STARTUP_TIMEOUT)
        long second = Long.parseLong(awaitAny(run, port, "/pid", RELOAD_TIMEOUT))
        second != first
        !ProcessHandle.of(first).present || !ProcessHandle.of(first).get().alive

        when: "the launcher is stopped"
        launcher = ProcessHandle.of(second).get()
        launcher.destroy()
        BuildResult result = run.get(60, TimeUnit.SECONDS) as BuildResult

        then:
        result.output.contains("The generation budget of the development process is spent: starting it again")
        portIsFree(port)
    }

    def "mnDev reloads a Kotlin controller, compiled with KSP"() {
        given:
        int port = freePort()
        writeBuild(port, """
                id("org.jetbrains.kotlin.jvm") version "$EMBEDDED_KOTLIN_VERSION"
                id("com.google.devtools.ksp") version "${System.getProperty("ksp2Version")}"
                id("org.jetbrains.kotlin.plugin.allopen") version "$EMBEDDED_KOTLIN_VERSION"
        """, 'implementation("org.jetbrains.kotlin:kotlin-stdlib")')
        writeSource("src/main/kotlin/example/Application.kt", """
            package example
            import io.micronaut.runtime.Micronaut
            object Application {
                @JvmStatic fun main(args: Array<String>) {
                    Micronaut.run(Application::class.java, *args)
                }
            }
        """)
        def writeKotlin = { String name, String path, String body ->
            writeSource("src/main/kotlin/example/${name}.kt", """
                package example
                import io.micronaut.http.MediaType
                import io.micronaut.http.annotation.Controller
                import io.micronaut.http.annotation.Get
                import io.micronaut.http.annotation.Produces
                @Controller("$path")
                class $name {
                    @Get
                    @Produces(MediaType.TEXT_PLAIN)
                    fun index(): String = "$body"
                }
            """)
        }

        expect:
        reloads(port, "[JAVA, KOTLIN] compiler(s)", writeKotlin)
    }

    def "mnDev reloads a Groovy controller"() {
        given:
        int port = freePort()
        writeBuild(port, 'id "groovy"', 'implementation("org.apache.groovy:groovy")')
        writeSource("src/main/groovy/example/Application.groovy", """
            package example
            import io.micronaut.runtime.Micronaut
            class Application {
                static void main(String[] args) {
                    Micronaut.run(Application, args)
                }
            }
        """)
        def writeGroovy = { String name, String path, String body ->
            writeSource("src/main/groovy/example/${name}.groovy", """
                package example
                import io.micronaut.http.MediaType
                import io.micronaut.http.annotation.Controller
                import io.micronaut.http.annotation.Get
                import io.micronaut.http.annotation.Produces
                @Controller("$path")
                class $name {
                    @Get
                    @Produces(MediaType.TEXT_PLAIN)
                    String index() {
                        "$body"
                    }
                }
            """)
        }

        expect:
        reloads(port, "[JAVA, GROOVY] compiler(s)", writeGroovy)
    }

    /**
     * Runs mnDev on a project, with a controller at /hello that the writer writes, then edited, and another added.
     */
    private boolean reloads(int port, String compilers, Closure writer) {
        writer("HelloController", "/hello", "one")
        CompletableFuture<Object> run = CompletableFuture.supplyAsync {
            try {
                configureRunner("mnDev").run()
            } catch (UnexpectedBuildFailure failure) {
                failure.buildResult
            }
        }
        awaitBody(run, port, "/hello", "one", STARTUP_TIMEOUT)
        writer("HelloController", "/hello", "two")
        awaitBody(run, port, "/hello", "two", RELOAD_TIMEOUT)
        writer("AddedController", "/added", "added")
        awaitBody(run, port, "/added", "added", RELOAD_TIMEOUT)
        assert get(port, "/hello") == "two"
        launcher = launchers().first()
        launcher.destroy()
        BuildResult result = run.get(60, TimeUnit.SECONDS) as BuildResult
        assert result.output.contains(compilers)
        assert !result.output.contains("Reload failed")
        true
    }

    def "mnDev reloads an edit of a project depended on that uses what that project alone compiles against"() {
        given: "a project whose code uses its implementation and compileOnly dependencies, which the application's compile classpath does not hold"
        int port = freePort()
        writeBuild(port, "", 'implementation(project(":lib"))')
        settingsFile << "\ninclude 'lib'\n"
        writeSource("lib/build.gradle", """
            plugins { id "java-library" }
            $repositoriesBlock
            dependencies {
                implementation("org.apache.commons:commons-lang3:3.17.0")
                compileOnly("org.jetbrains:annotations:26.0.1")
            }
        """)
        writeGreeter("one")
        writeJavaSources()
        writeSource("src/main/java/example/HelloController.java", """
            package example;
            import io.micronaut.http.MediaType;
            import io.micronaut.http.annotation.Controller;
            import io.micronaut.http.annotation.Get;
            import io.micronaut.http.annotation.Produces;
            @Controller("/hello")
            public class HelloController {
                @Get
                @Produces(MediaType.TEXT_PLAIN)
                public String index() {
                    return lib.Greeter.greeting();
                }
            }
        """)

        when: "mnDev runs in the background"
        CompletableFuture<Object> run = CompletableFuture.supplyAsync {
            try {
                configureRunner("mnDev").run()
            } catch (UnexpectedBuildFailure failure) {
                failure.buildResult
            }
        }

        then: "the application answers with the class as written"
        awaitBody(run, port, "/hello", "ONE", STARTUP_TIMEOUT)
        long pid = Long.parseLong(awaitAny(run, port, "/pid", RELOAD_TIMEOUT))

        when: "the class of the project depended on is edited"
        launcher = ProcessHandle.of(pid).get()
        writeGreeter("two")

        then: "it compiles, and the edit answers from the same process"
        awaitBody(run, port, "/hello", "TWO", RELOAD_TIMEOUT)
        awaitAny(run, port, "/pid", RELOAD_TIMEOUT) == String.valueOf(pid)

        when: "the launcher is stopped"
        launcher.destroy()
        run.get(60, TimeUnit.SECONDS)

        then:
        launcher.onExit().get(30, TimeUnit.SECONDS) != null
    }

    private void writeProject(int port) {
        writeBuild(port)
        writeJavaSources()
    }

    private void writeBuild(int port, String plugins = "", String dependencies = "") {
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                $plugins
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
            $repositoriesBlock
            $DEV_CORE_REPOSITORY
            // the application and the launcher both on the core that has development mode
            configurations.configureEach {
                resolutionStrategy.eachDependency {
                    if (requested.group == "io.micronaut") {
                        useVersion("$DEV_CORE_VERSION")
                    }
                }
            }
            dependencies {
                // the JSON the server answers errors with, from core
                runtimeOnly("io.micronaut:micronaut-jackson-databind")
                runtimeOnly("ch.qos.logback:logback-classic")
                $dependencies
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
    }

    private void writeJavaSources() {
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

    /**
     * The launchers of this project among the processes this JVM started, through the build running in it.
     */
    private List<ProcessHandle> launchers() {
        String project = baseDir.fileName.toString()
        new ArrayList<>(ProcessHandle.current().descendants().filter { process ->
            process.info().arguments().map { arguments ->
                arguments.any { it.contains("MicronautDevMain") } && arguments.any { it.contains(project) }
            }.orElse(false)
        }.toList())
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

    private void writeGreeter(String body) {
        File source = file("lib/src/main/java/lib/Greeter.java")
        long previous = source.exists() ? source.lastModified() : 0
        writeSource(source, """
            package lib;
            import org.apache.commons.lang3.StringUtils;
            import org.jetbrains.annotations.NotNull;
            public class Greeter {
                @NotNull
                public static String greeting() {
                    return StringUtils.upperCase("$body");
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
