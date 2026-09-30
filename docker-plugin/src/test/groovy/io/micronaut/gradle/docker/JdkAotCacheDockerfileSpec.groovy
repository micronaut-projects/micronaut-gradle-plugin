package io.micronaut.gradle.docker

import io.micronaut.gradle.AbstractGradleBuildSpec
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf
import spock.lang.Issue

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

@Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/1379")
@IgnoreIf({ os.windows })
class JdkAotCacheDockerfileSpec extends AbstractGradleBuildSpec {

    private static final String DEFAULT_DOCKERFILE = """FROM eclipse-temurin:25-jre
WORKDIR /home/app
COPY --link layers/libs /home/app/libs
COPY --link layers/app /home/app/
COPY --link layers/resources /home/app/resources
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/home/app/application.jar"]
"""

    private static final String TRAIN = '"bash", "/home/app/jdk-aot-cache/train.sh", "--cache", "/home/app/application.aot", "--timeout", "120", "--compatible-oop-compression"'

    private static final String ENTRYPOINT = 'ENTRYPOINT ["java", "-XX:AOTCache=/home/app/application.aot", "-XX:+UseG1GC", "-jar", "/home/app/application.jar"]'

    private static final String STARTS_THE_APPLICATION = "JDK AOT cache: the application's Micronaut version has no 'load' training mode (micronaut.application.training.mode), " +
        "so the training run starts the application while the image is built, where the services it needs to start must be available. " +
        "Use a Micronaut version that has the 'load' mode to train without starting the application, or set trainingMode = 'start' to confirm this run"

    def "the Dockerfile and the layers are unchanged when the JDK AOT cache is disabled"() {
        given:
        withApplication("""
            micronaut {
                docker {
                    jdkAotCache {
                        trainingPaths = ["/hello"]
                    }
                }
            }
        """)

        when:
        def result = build('dockerfile', 'buildLayers')

        then:
        result.task(":dockerfile").outcome == TaskOutcome.SUCCESS
        file("build/docker/main/Dockerfile").text == DEFAULT_DOCKERFILE
        !file("build/docker/main/jdk-aot-cache").exists()
        file("build/docker/main/layers/resources/application.yml").exists()
        classPathOf(file("build/docker/main/layers/app/application.jar")).startsWith("resources/ classes/ libs/")
    }

    def "the main image trains a JDK AOT cache"() {
        given:
        withApplication("""
            micronaut {
                docker {
                    jdkAotCache {
                        enabled = true
                        trainingMode = "start"
                        trainingPaths = ["/hello", "/hello?name=training"]
                    }
                }
            }
        """)

        when:
        def result = build('dockerfile', 'buildLayers')

        then:
        result.task(":dockerfile").outcome == TaskOutcome.SUCCESS
        file("build/docker/main/Dockerfile").text == """FROM eclipse-temurin:25-jre
WORKDIR /home/app
COPY --link layers/libs /home/app/libs
COPY --link layers/app /home/app/
COPY --link layers/resources /home/app/resources
EXPOSE 8080
COPY --link jdk-aot-cache/train.sh /home/app/jdk-aot-cache/train.sh
RUN ["bash", "/home/app/jdk-aot-cache/train.sh", "--cache", "/home/app/application.aot", "--timeout", "120", "--compatible-oop-compression", "--port", "8080", "--path", "/hello", "--path", "/hello?name=training", "--", "java", "-XX:+UseG1GC", "-jar", "/home/app/application.jar"]
ENTRYPOINT ["java", "-XX:AOTCache=/home/app/application.aot", "-XX:+UseG1GC", "-jar", "/home/app/application.jar"]
"""
        file("build/docker/main/jdk-aot-cache/train.sh").text == MicronautDockerfile.getResourceAsStream("jdk-aot-cache/train.sh").text

        and: "the resources are a JAR, listed first, so the class path has JARs only"
        file("build/docker/main/layers/resources").list() as List == ["hello-world-runner-resources.jar"]
        entriesOf(file("build/docker/main/layers/resources/hello-world-runner-resources.jar")).contains("application.yml")
        def classPath = classPathOf(file("build/docker/main/layers/app/application.jar")).split(" ")
        classPath[0] == "resources/hello-world-runner-resources.jar"
        classPath.drop(1).every { it.startsWith("libs/") && it.endsWith(".jar") }
    }

    def "the training run and the entrypoint share the JVM arguments"() {
        given:
        withApplication("""
            micronaut {
                docker {
                    jdkAotCache {
                        enabled = true
                        trainingMode = "start"
                        trainingPaths = ["/hello"]
                        trainingTimeout = 300
                        compatibleOopCompression = false
                        strictProbes = 5
                    }
                }
            }

            tasks.named("dockerfile") {
                args("-XX:+UseSerialGC", "-Xmx256m")
                exposedPorts = [9090, 8443]
            }
        """)

        when:
        build('dockerfile')

        then:
        def lines = file("build/docker/main/Dockerfile").readLines()
        lines.contains('RUN ["bash", "/home/app/jdk-aot-cache/train.sh", "--cache", "/home/app/application.aot", "--timeout", "300", "--strict-probes", "5", "--port", "9090", "--path", "/hello", "--", "java", "-XX:+UseSerialGC", "-Xmx256m", "-jar", "/home/app/application.jar"]')
        lines.last() == 'ENTRYPOINT ["java", "-XX:AOTCache=/home/app/application.aot", "-XX:+UseSerialGC", "-Xmx256m", "-jar", "/home/app/application.jar"]'
    }

    def "can be configured with the Kotlin DSL"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        kotlinBuildFile << """
            plugins {
                id("io.micronaut.minimal.application")
                id("io.micronaut.docker")
            }

            micronaut {
                version("$micronautVersion")
                docker {
                    jdkAotCache {
                        enabled.set(true)
                        trainingMode.set("start")
                        trainingPaths.set(listOf("/hello"))
                        strictProbes.set(2)
                    }
                }
            }

            ${getRepositoriesBlock('kotlin')}

            application { mainClass.set("example.Application") }
        """

        when:
        // Not in process: when the test JVM has a Java agent (JaCoCo on CI), an in-process build loads the
        // plugins from the test JVM's class path, which Gradle removes from the Kotlin script class path,
        // so the accessors of micronaut { } and docker { } are not typed and the script does not compile
        configureRunner('dockerfile').withDebug(false).build()

        then:
        file("build/docker/main/Dockerfile").readLines().contains('RUN ["bash", "/home/app/jdk-aot-cache/train.sh", "--cache", "/home/app/application.aot", "--timeout", "120", "--compatible-oop-compression", "--strict-probes", "2", "--port", "8080", "--path", "/hello", "--", "java", "-XX:+UseG1GC", "-jar", "/home/app/application.jar"]')
    }

    def "trains after the additional instructions"() {
        given:
        withApplication("""
            micronaut.docker.jdkAotCache.enabled = true

            tasks.named("dockerfile") {
                arg("MICRONAUT_ENVIRONMENTS=training")
            }
        """)

        when:
        build('dockerfile')

        then:
        def lines = file("build/docker/main/Dockerfile").readLines()
        def arg = lines.indexOf('ARG MICRONAUT_ENVIRONMENTS=training')
        arg > 0
        arg < lines.findIndexOf { it.startsWith("COPY --link jdk-aot-cache/") }
        lines.findIndexOf { it.startsWith("RUN ") } == lines.size() - 2
    }

    def "the default training run starts the application when its Micronaut version has no load mode"() {
        given:
        withApplication("""
            micronaut.docker.jdkAotCache {
                enabled = true
                $configuration
            }
        """)

        when:
        def result = build('dockerfile')

        then:
        def lines = file("build/docker/main/Dockerfile").readLines()
        lines.contains("RUN [$TRAIN, $expected, \"-XX:+UseG1GC\", \"-jar\", \"/home/app/application.jar\"]" as String)
        lines.last() == ENTRYPOINT
        result.output.contains(STARTS_THE_APPLICATION) == logged

        where:
        configuration                                         | expected                                                       | logged
        ''                                                    | '"--port", "8080", "--", "java"'                               | true
        'trainingMode = "start"'                              | '"--port", "8080", "--", "java"'                               | false
        'trainingMode = "START"; trainingPaths = ["/hello"]'  | '"--port", "8080", "--path", "/hello", "--", "java"'           | false
    }

    def "on a Micronaut core that has #core, the training run of [#configuration] gets [#expected]"() {
        given:
        withApplication("""
            micronaut.docker.jdkAotCache {
                enabled = true
                $configuration
            }
        """)
        withFakeCore(mode)

        when:
        def result = build('dockerfile')

        then:
        def lines = file("build/docker/main/Dockerfile").readLines()
        lines.contains("RUN [$TRAIN, \"--training-run\", \"--\", \"java\", \"-Dmicronaut.application.training.enabled=true\", $expected\"-XX:+UseG1GC\", \"-jar\", \"/home/app/application.jar\"]" as String)
        result.output.contains(STARTS_THE_APPLICATION) == logged

        and: "the training properties are those of the training run only: the image starts the application"
        lines.last() == ENTRYPOINT
        lines.count { it.contains("micronaut.application.training") } == 1

        where:
        mode  | configuration                                                              | expected                                                                                                                                        | logged
        true  | ''                                                                         | '"-Dmicronaut.application.training.mode=load", '                                                                                                | false
        true  | 'trainingMode = "load"'                                                    | '"-Dmicronaut.application.training.mode=load", '                                                                                                | false
        true  | 'trainingMode = "start"'                                                   | '"-Dmicronaut.application.training.mode=start", '                                                                                               | false
        true  | 'trainingMode = "start"; trainingPaths = ["/hello", "/hello?name=training"]' | '"-Dmicronaut.application.training.mode=start", "-Dmicronaut.application.training.warmup.paths=/hello,/hello?name=training", '                  | false
        false | ''                                                                         | ''                                                                                                                                              | true
        false | 'trainingMode = "start"'                                                   | ''                                                                                                                                              | false
        false | 'trainingMode = "start"; trainingPaths = ["/hello", "/hello?name=training"]' | '"-Dmicronaut.application.training.warmup.paths=/hello,/hello?name=training", '                                                                 | false

        core = mode ? "the switch and the load mode" : "the switch but not the load mode"
    }

    def "the load mode trains an image that exposes no port"() {
        given:
        withApplication("""
            micronaut.docker.jdkAotCache.enabled = true

            tasks.named("dockerfile") {
                exposedPorts = []
            }
        """)
        withFakeCore(true)

        when:
        build('dockerfile')

        then:
        def lines = file("build/docker/main/Dockerfile").readLines()
        lines.contains("RUN [$TRAIN, \"--training-run\", \"--\", \"java\", \"-Dmicronaut.application.training.enabled=true\", \"-Dmicronaut.application.training.mode=load\", \"-XX:+UseG1GC\", \"-jar\", \"/home/app/application.jar\"]" as String)
        lines.last() == ENTRYPOINT
    }

    def "the dockerfile task can select another training mode than the extension"() {
        given:
        withApplication("""
            micronaut.docker.jdkAotCache.enabled = true

            tasks.named("dockerfile") {
                jdkAotCache.trainingMode = "start"
            }
        """)
        withFakeCore(true)

        when:
        build('dockerfile')

        then:
        file("build/docker/main/Dockerfile").readLines().any { it.startsWith("RUN [") && it.contains('"-Dmicronaut.application.training.mode=start"') }
    }

    def "the load mode fails on a Micronaut core that has the switch but not the load mode"() {
        given:
        withApplication("""
            micronaut.docker.jdkAotCache {
                enabled = true
                trainingMode = "load"
            }
        """)
        withFakeCore(false)

        when:
        def result = fails('dockerfile')

        then:
        result.output.contains("The JDK AOT cache training mode is 'load', but the application's Micronaut version has no such mode")
    }

    def "is compatible with the configuration cache"() {
        given:
        withApplication("""
            micronaut.docker.jdkAotCache {
                enabled = true
                trainingMode = "start"
                trainingPaths = ["/hello"]
            }
        """)

        when:
        def result = build('dockerfile', '--configuration-cache')

        then:
        result.task(":dockerfile").outcome == TaskOutcome.SUCCESS
        def output = result.output.toLowerCase()
        output.contains('configuration cache entry stored') ||
            output.contains('support for using a java agent with testkit builds is not yet implemented with the configuration cache')
        file("build/docker/main/Dockerfile").readLines().any { it.startsWith('RUN ["bash", "/home/app/jdk-aot-cache/train.sh"') }
        file("build/docker/main/jdk-aot-cache/train.sh").exists()
    }

    def "fails when #description"() {
        given:
        withApplication("""
            micronaut.docker.jdkAotCache.enabled = true
            $configuration
        """)

        when:
        def result = fails('dockerfile')

        then:
        result.output.contains(message)

        where:
        description                                  | configuration                                                                                   | message
        "the JDK is older than 25"                   | 'tasks.named("dockerfile") { jdkVersion = JavaVersion.VERSION_21 }'                             | "The JDK AOT cache needs JDK 25 or later, but the jdkVersion of the dockerfile task is 21"
        "the build strategy is not the default"      | 'micronaut.runtime("lambda_provided")'                                                          | "The JDK AOT cache only supports the DEFAULT Docker build strategy, but the dockerfile task uses LAMBDA"
        "the build adds its own entrypoint"          | 'tasks.named("dockerfile") { entryPoint("java", "-jar", "app.jar") }'                           | "The JDK AOT cache needs the ENTRYPOINT generated by the dockerfile task"
        "a training path is not absolute"            | 'micronaut.docker.jdkAotCache { trainingMode = "start"; trainingPaths = ["hello"] }'            | "Invalid JDK AOT cache training path 'hello'"
        "a training path has a comma"                | 'micronaut.docker.jdkAotCache { trainingMode = "start"; trainingPaths = ["/hello?a=1,2"] }'     | "Invalid JDK AOT cache training path '/hello?a=1,2'"
        "the training mode is unknown"               | 'micronaut.docker.jdkAotCache.trainingMode = "laod"'                                            | "Invalid JDK AOT cache training mode 'laod': the training mode is 'load' or 'start'"
        "training paths are set without a mode"      | 'micronaut.docker.jdkAotCache.trainingPaths = ["/hello"]'                                       | "The JDK AOT cache training paths are only requested by a training run that starts the application, but the training mode is not set: set trainingMode to 'start', or remove the training paths"
        "training paths are set in the load mode"    | 'micronaut.docker.jdkAotCache { trainingMode = "load"; trainingPaths = ["/hello"] }'            | "The JDK AOT cache training paths are only requested by a training run that starts the application, but the training mode is 'load': set trainingMode to 'start', or remove the training paths"
        "the Micronaut version has no load mode"     | 'micronaut.docker.jdkAotCache.trainingMode = "load"'                                            | "The JDK AOT cache training mode is 'load', but the application's Micronaut version has no such mode: it needs a Micronaut Core with the micronaut.application.training.mode property. Upgrade Micronaut, or remove trainingMode to train with a run that starts the application"
        "the image exposes no port"                  | 'tasks.named("dockerfile") { exposedPorts = [] }'                                               | "the image exposes no port"
    }

    def "fails when the JDK AOT cache is only enabled on the task"() {
        given:
        withApplication('tasks.named("dockerfile") { jdkAotCache.enabled = true }')

        when:
        def result = fails('dockerfile')

        then:
        result.output.contains("The JDK AOT cache is enabled on the dockerfile task, but it must be enabled with micronaut.docker.jdkAotCache.enabled")
    }

    /**
     * Replaces the Micronaut core of an application set up with {@link #withApplication} with a JAR that has the
     * training run switch, and its modes if asked. The plugin only looks for the two properties in the class bytes.
     */
    private void withFakeCore(boolean mode) {
        def fakeCore = file("fake-core/micronaut-context-99.0.0.jar")
        fakeCore.parentFile.mkdirs()
        new JarOutputStream(fakeCore.newOutputStream()).withCloseable { jar ->
            jar.putNextEntry(new JarEntry("io/micronaut/runtime/Micronaut.class"))
            jar.write("io/micronaut/runtime/ApplicationConfiguration micronaut.application.training.enabled".getBytes("UTF-8"))
            jar.closeEntry()
            jar.putNextEntry(new JarEntry("io/micronaut/runtime/ApplicationConfiguration.class"))
            jar.write("micronaut.application.training.enabled ${mode ? 'micronaut.application.training.mode' : ''}".getBytes("UTF-8"))
            jar.closeEntry()
        }
        buildFile << """
            configurations.runtimeClasspath {
                exclude(group: "io.micronaut", module: "micronaut-context")
            }

            dependencies {
                runtimeOnly(files("fake-core/micronaut-context-99.0.0.jar"))
            }
        """
    }

    private void withApplication(String configuration) {
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.docker"
            }

            micronaut {
                version "$micronautVersion"
            }

            $repositoriesBlock

            application { mainClass = "example.Application" }

            $configuration
        """
        file("src/main/java/example/Application.java").tap {
            parentFile.mkdirs()
            text = """
package example;

class Application {
    public static void main(String... args) {
    }
}
"""
        }
        file("src/main/resources/application.yml").tap {
            parentFile.mkdirs()
            text = "micronaut.application.name: hello-world\n"
        }
    }

    private static String classPathOf(File jar) {
        new JarFile(jar).withCloseable { it.manifest.mainAttributes.getValue("Class-Path") }
    }

    private static List<String> entriesOf(File jar) {
        new JarFile(jar).withCloseable { it.entries().collect { it.name } }
    }
}
