package io.micronaut.gradle.docker

import io.micronaut.gradle.AbstractGradleBuildSpec
import io.micronaut.gradle.fixtures.AbstractFunctionalTest
import org.gradle.testkit.runner.TaskOutcome
import org.opentest4j.TestAbortedException
import spock.lang.IgnoreIf
import spock.lang.Issue

import java.util.concurrent.TimeUnit
import java.util.jar.JarFile

@Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/1379")
@IgnoreIf({ os.windows || !AbstractGradleBuildSpec.dockerAvailable })
class JdkAotCacheDockerFunctionalTest extends AbstractFunctionalTest {

    private static final String CACHE = "/home/app/application.aot"

    private static final String LABEL = "io.micronaut.gradle.jdk-aot-cache-test"

    private static final String STARTS_THE_APPLICATION = "JDK AOT cache: the application's Micronaut version has no 'load' training mode (micronaut.application.training.mode), " +
        "so the training run starts the application while the image is built, where the services it needs to start must be available. " +
        "Use a Micronaut version that has the 'load' mode to train without starting the application, or set trainingMode = 'start' to confirm this run"

    private static final String APPLICATION_DID_NOT_START = "[jdk-aot-cache] This training run starts the application, and its Micronaut version has no 'load' training mode that trains without starting it: " +
        "the services that the application needs to start must be available while the image is built, or a configuration for the training run must replace them"

    /**
     * Until a released Micronaut has the load training mode (micronaut-projects/micronaut-core#13402), these point
     * the test application at a build of Micronaut core that has it: a Maven repository directory and the version
     * of the core modules in it. Without them, the application uses the core of the Micronaut Platform under test.
     * The functional test task declares both as inputs.
     */
    private static final String CORE_REPOSITORY = System.getenv("JDK_AOT_CACHE_TEST_CORE_REPOSITORY")
    private static final String CORE_VERSION = System.getenv("JDK_AOT_CACHE_TEST_CORE_VERSION")
    private static final boolean CORE_UNDER_TEST = CORE_REPOSITORY && CORE_VERSION

    /**
     * A released Micronaut Platform whose Micronaut core has no training run switch, so the plugin's script drives
     * the training run. The tests of the script use it whatever the Micronaut Platform under test is, because
     * every Micronaut version released before the switch keeps training with the script.
     */
    private static final String MICRONAUT_WITHOUT_TRAINING_RUN = "5.0.2"

    /**
     * The class of Micronaut core that runs the load training mode. The plugin does not look at it: it looks for
     * the names of the two properties in two other classes.
     */
    private static final String TRAINING_LOAD_CLASS = "io/micronaut/runtime/TrainingLoad.class"

    private final String id = UUID.randomUUID().toString()
    private final String image = "micronaut-gradle-jdk-aot-cache-$id"

    /**
     * The Micronaut Platform version of the test application, set by {@link #withApplication}: null for the one
     * under test.
     */
    private String micronaut

    def cleanup() {
        docker("rmi", "-f", "$image:main", "$image:optimized")
        // A failed image build leaves the container of the failed step and the image of the step before it behind.
        // Both have the label that the Dockerfile of the test application sets
        def containers = docker("ps", "-a", "-q", "--filter", "label=$LABEL=$id").output.readLines()
        if (containers) {
            docker("rm", "-f", *containers)
        }
        def images = docker("images", "-a", "-q", "--filter", "label=$LABEL=$id").output.readLines().unique()
        if (images) {
            docker("rmi", "-f", *images)
        }
    }

    def "the default training run trains an application that cannot start while the image is built"() {
        given:
        withApplication(database: true)

        when:
        def dockerfile = build("dockerfile", "buildLayers")
        if (!coreHasLoadMode()) {
            throw new TestAbortedException("This test needs a Micronaut version that has the load training mode: " +
                "the Micronaut core of Micronaut Platform $micronautVersion has no $TRAINING_LOAD_CLASS")
        }

        then: "the plugin finds the load mode of that Micronaut core, and it is the default"
        trainingRun().contains('"--training-run", "--", "java", "-Dmicronaut.application.training.enabled=true", "-Dmicronaut.application.training.mode=load"')
        !dockerfile.output.contains(STARTS_THE_APPLICATION)

        when:
        def result = build("dockerBuild")

        then: "the training run did not start the application"
        result.task(":dockerBuild").outcome == TaskOutcome.SUCCESS
        result.output.contains("-Dmicronaut.application.training.enabled=true -Dmicronaut.application.training.mode=load")
        !result.output.contains("Cannot connect to the database")
        !result.output.contains("Startup completed")
        result.output.contains("[jdk-aot-cache] Wrote $CACHE")
        result.output.contains("[jdk-aot-cache] 3 strict probes passed")

        when: "the application starts in strict mode, without its database"
        def strict = startApplication("$image:main", "-XX:AOTMode=on", "-XX:AOTCache=$CACHE", "-XX:+UseG1GC", "-Xlog:class+load=info")

        then: "the cache serves its classes, and it cannot start"
        strict.output.contains("example.Database source: shared objects file")
        strict.output.contains("example.HelloController source: shared objects file")
        strict.output.contains("Cannot connect to the database")
        strict.exitCode != 0
    }

    def "on a Micronaut version without the load mode, the default training run starts the application"() {
        given:
        withApplication(database: true, micronaut: MICRONAUT_WITHOUT_TRAINING_RUN)

        when:
        def dockerfile = build("dockerfile", "buildLayers")

        then: "the plugin's script drives the training run, and the task that generates the Dockerfile says so"
        !coreHasLoadMode()
        scriptDrivesTheTrainingRun()
        dockerfile.output.contains(STARTS_THE_APPLICATION)

        when:
        def result = fails("dockerBuild")

        then: "the application cannot start"
        result.output.contains("Cannot connect to the database")
        result.output.contains("[jdk-aot-cache] ERROR: the application exited with status 1 before it answered on port 8080")

        and: "the image build says why it was started, which the task that generates the Dockerfile does not repeat"
        result.task(":dockerfile").outcome == TaskOutcome.UP_TO_DATE
        !result.output.contains(STARTS_THE_APPLICATION)
        result.output.contains(APPLICATION_DID_NOT_START)
    }

    def "the main image trains a JDK AOT cache that serves application and dependency classes, with #description"() {
        given:
        withApplication(trainingPaths: ["/hello", "/hello?name=training"], micronaut: version)

        when:
        def result = build("dockerBuild")

        then:
        result.task(":dockerBuild").outcome == TaskOutcome.SUCCESS
        requestsOf(["/hello", "/hello?name=training"]).every { result.output.contains(it) }
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

        where:
        [description, version] << micronautVersions()
    }

    def "a training path that the plugin's script gets a 404 for fails the image build"() {
        given: "the script sends the requests: the warm-up of Micronaut's training run switch logs a 404 and goes on"
        withApplication(trainingPaths: ["/hello", "/missing-$id"], micronaut: MICRONAUT_WITHOUT_TRAINING_RUN)

        when:
        def result = fails("dockerBuild")

        then:
        !coreHasLoadMode()
        scriptDrivesTheTrainingRun()
        result.output.contains("[jdk-aot-cache] GET /hello: 200")
        result.output.contains("[jdk-aot-cache] ERROR: GET /missing-$id answered with status 404")

        and: "the application started"
        !result.output.contains(APPLICATION_DID_NOT_START)
    }

    def "the optimized image trains a JDK AOT cache that serves application and dependency classes, with #description"() {
        given:
        withApplication(trainingPaths: ["/hello"], extraPlugin: 'id "io.micronaut.aot"', micronaut: version)

        when:
        def result = build("optimizedDockerBuild")

        then:
        result.task(":optimizedDockerBuild").outcome == TaskOutcome.SUCCESS
        requestsOf(["/hello"], "optimized").every { result.output.contains(it) }

        when:
        def strict = startApplication("$image:optimized", "-XX:AOTMode=on", "-XX:AOTCache=$CACHE", "-XX:+UseG1GC", "-Xlog:class+load=info")

        then:
        strict.exitCode == 0
        strict.output.contains("example.HelloController source: shared objects file")
        strict.output.contains("io.micronaut.http.server.netty.NettyHttpServer source: shared objects file")

        where:
        [description, version] << micronautVersions()
    }

    /**
     * The Micronaut versions of the tests of the start mode: the one under test and, unless it is the same one,
     * the released one without the training run switch. So the plugin's script stays tested once the Micronaut
     * Platform under test has the switch, which then drives the training run of that version.
     *
     * @return for each version, its description and the version, null for the one under test
     */
    private static List<List<String>> micronautVersions() {
        List<List<String>> versions = [["the Micronaut version under test", null]]
        if (CORE_UNDER_TEST || System.getProperty("micronautVersion") != MICRONAUT_WITHOUT_TRAINING_RUN) {
            versions << ["Micronaut $MICRONAUT_WITHOUT_TRAINING_RUN and the plugin's script".toString(), MICRONAUT_WITHOUT_TRAINING_RUN]
        }
        versions
    }

    /**
     * Whether the Micronaut core of the test application has the load training mode. The answer comes from the
     * Micronaut core JAR that the image gets, and from a class that the plugin does not look at. It does not come
     * from the Dockerfile that the plugin generates: a test that asked the plugin would agree with whatever the
     * plugin detects. The layers of the image must be built.
     */
    private boolean coreHasLoadMode(String imageName = "main") {
        List<File> cores = ["libs", "snapshot_libs"]
            .collectMany { (file("build/docker/$imageName/layers/$it").listFiles() ?: []) as List<File> }
            .findAll { it.name ==~ /micronaut-context-\d.*\.jar/ }
        assert cores.size() == 1
        boolean loadMode = new JarFile(cores[0]).withCloseable { it.getEntry(TRAINING_LOAD_CLASS) != null }
        if (micronaut == MICRONAUT_WITHOUT_TRAINING_RUN) {
            assert !loadMode: "${cores[0].name} must not have the load training mode"
        } else if (CORE_UNDER_TEST) {
            assert loadMode: "JDK_AOT_CACHE_TEST_CORE_REPOSITORY and JDK_AOT_CACHE_TEST_CORE_VERSION must point at a Micronaut core that has the load training mode, but the application got ${cores[0].name}"
        }
        loadMode
    }

    /**
     * The training instruction of a generated Dockerfile.
     */
    private String trainingRun(String imageName = "main") {
        file("build/docker/$imageName/Dockerfile").readLines().find { it.startsWith('RUN ["bash", "/home/app/jdk-aot-cache/train.sh"') }
    }

    /**
     * Whether the plugin's script starts the application, sends the requests and stops it, which it does when
     * Micronaut core has no training run switch.
     */
    private boolean scriptDrivesTheTrainingRun(String imageName = "main") {
        trainingRun(imageName).contains('"--port"')
    }

    /**
     * What an image build logs when its training run in start mode has requested the paths. What the Micronaut
     * core of the test application has decides who must have sent the requests: Micronaut's own training run,
     * whose warm-up logs a summary, or the plugin's script, which logs a line for each request.
     */
    private List<String> requestsOf(List<String> paths, String imageName = "main") {
        if (coreHasLoadMode(imageName)) {
            assert trainingRun(imageName).contains('"--training-run", "--", "java", "-Dmicronaut.application.training.enabled=true", "-Dmicronaut.application.training.mode=start", ' +
                "\"-Dmicronaut.application.training.warmup.paths=${paths.join(',')}\"")
            return ["Training warm-up sent ${paths.size()} GET requests".toString()]
        }
        assert scriptDrivesTheTrainingRun(imageName)
        paths.collect { "[jdk-aot-cache] GET $it: 200".toString() }
    }

    /**
     * Writes the test application.
     *
     * @param options trainingPaths: the paths of a training run in start mode, none for the default training mode;
     * extraPlugin: another plugin to apply; database: whether the application connects to a database as it starts;
     * micronaut: the Micronaut Platform version, which is the one under test if there is none
     */
    private void withApplication(Map<String, Object> options) {
        List<String> trainingPaths = options.trainingPaths ?: []
        micronaut = options.micronaut
        String coreUnderTest = CORE_UNDER_TEST && !micronaut ? """
            repositories {
                maven { url = uri("$CORE_REPOSITORY") }
            }

            configurations.configureEach {
                resolutionStrategy.eachDependency { details ->
                    if (details.requested.group == "io.micronaut") {
                        details.useVersion("$CORE_VERSION")
                    }
                }
            }
        """ : ""
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.docker"
                ${options.extraPlugin ?: ""}
            }

            micronaut {
                version "${micronaut ?: micronautVersion}"
                runtime "netty"
                docker {
                    jdkAotCache {
                        enabled = true
                        ${trainingPaths ? "trainingMode = 'start'" : ""}
                        trainingPaths = [${trainingPaths.collect { "'$it'" }.join(", ")}]
                        strictProbes = 3
                    }
                }
            }

            $repositoriesBlock
            $coreUnderTest

            application { mainClass = "example.Application" }

            dependencies {
                runtimeOnly("ch.qos.logback:logback-classic")
                runtimeOnly("io.micronaut.serde:micronaut-serde-jackson")
            }

            tasks.withType(com.bmuschko.gradle.docker.tasks.image.DockerBuildImage).configureEach {
                images = ["$image:\${name == 'dockerBuild' ? 'main' : 'optimized'}"]
            }

            tasks.withType(io.micronaut.gradle.docker.MicronautDockerfile).configureEach {
                label(["$LABEL": "$id"])
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
        if (options.database) {
            // Stands for a datasource: nothing listens on that port, in the image build or in the test
            file("src/main/java/example/Database.java").text = """
package example;

import io.micronaut.context.annotation.Context;

import java.io.IOException;
import java.net.Socket;

@Context
public class Database {
    public Database() {
        try (Socket socket = new Socket("127.0.0.1", 1)) {
            socket.getOutputStream().write(0);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot connect to the database", e);
        }
    }
}
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
