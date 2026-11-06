package io.micronaut.gradle.docker

import io.micronaut.gradle.AbstractGradleBuildSpec
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf
import spock.lang.Issue

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

/**
 * The JDK AOT cache that the JVM images train when micronaut.docker.jdkAotCache.enabled is not set.
 */
@Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/1389")
@IgnoreIf({ os.windows })
class JdkAotCacheDefaultSpec extends AbstractGradleBuildSpec {

    private static final String DEFAULT_DOCKERFILE = """FROM eclipse-temurin:25-jre
WORKDIR /home/app
COPY --link layers/libs /home/app/libs
COPY --link layers/app /home/app/
COPY --link layers/resources /home/app/resources
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/home/app/application.jar"]
"""

    private static final String TRAINED_BY_DEFAULT = "JDK AOT cache: the image trains one by default"

    def "with a Micronaut core that has the load mode, the main image trains a cache by default, and falls back to no cache"() {
        given:
        withApplication("")
        withFakeCore(true)

        when:
        def result = build('dockerfile', 'buildLayers')

        then: "the training run loads the application, and the ENTRYPOINT reads the cache from the argument file that the training writes"
        result.output.contains(TRAINED_BY_DEFAULT)
        file("build/docker/main/Dockerfile").text == """FROM eclipse-temurin:25-jre
WORKDIR /home/app
COPY --link layers/libs /home/app/libs
COPY --link layers/app /home/app/
COPY --link layers/resources /home/app/resources
EXPOSE 8080
COPY --link jdk-aot-cache/train.sh /home/app/jdk-aot-cache/train.sh
RUN ["bash", "/home/app/jdk-aot-cache/train.sh", "--fallback", "/home/app/jdk-aot-cache/cache.args", "--cache", "/home/app/application.aot", "--timeout", "120", "--compatible-oop-compression", "--training-run", "--", "java", "-Dmicronaut.application.training.enabled=true", "-Dmicronaut.application.training.mode=load", "-XX:+UseG1GC", "-jar", "/home/app/application.jar"]
ENTRYPOINT ["java", "@/home/app/jdk-aot-cache/cache.args", "-XX:+UseG1GC", "-jar", "/home/app/application.jar"]
"""
        file("build/docker/main/jdk-aot-cache/train.sh").exists()

        and: "the class path has JARs only"
        file("build/docker/main/layers/resources").list() as List == ["hello-world-runner-resources.jar"]
        classPathOf(file("build/docker/main/layers/app/application.jar")).startsWith("resources/hello-world-runner-resources.jar libs/")
    }

    def "the default is compatible with the configuration cache"() {
        given:
        withApplication("")
        withFakeCore(true)

        when:
        def result = build('dockerfile', '--configuration-cache')

        then:
        result.task(":dockerfile").outcome == TaskOutcome.SUCCESS
        def output = result.output.toLowerCase()
        output.contains('configuration cache entry stored') ||
            output.contains('support for using a java agent with testkit builds is not yet implemented with the configuration cache')
        file("build/docker/main/Dockerfile").text.contains('"--fallback"')
    }

    def "the default does not train a cache when #description"() {
        given:
        withApplication(configuration)
        withFakeCore(mode)

        when:
        def result = build('dockerfile', 'buildLayers', '--info')

        then: "the image is the one without a cache, and the dockerfile task says why"
        !result.output.contains(TRAINED_BY_DEFAULT)
        result.output.contains("JDK AOT cache: not trained by default, because $reason")
        def dockerfile = file("build/docker/main/Dockerfile").text
        !dockerfile.contains("train.sh")
        !dockerfile.contains("AOTCache")
        unchanged == null || dockerfile == DEFAULT_DOCKERFILE.replace("25-jre", unchanged)
        !file("build/docker/main/jdk-aot-cache").exists()
        file("build/docker/main/layers/resources/application.yml").exists()
        classPathOf(file("build/docker/main/layers/app/application.jar")).startsWith("resources/ classes/ libs/")

        where:
        description                               | mode  | configuration                                                                            | unchanged | reason
        "the Micronaut core has no load mode"     | false | ''                                                                                       | "25-jre"  | "the application's Micronaut version has no LOAD training mode"
        "the JDK is older than 25"                | true  | 'tasks.named("dockerfile") { jdkVersion = JavaVersion.VERSION_21 }'                      | "21-jre"  | "the jdkVersion is 21"
        "the base image is not the default one"   | true  | 'tasks.named("dockerfile") { baseImage = "eclipse-temurin:25-jre-alpine" }'              | null      | "the base image is not the default one"
        "the build strategy is not the default"   | true  | 'micronaut.runtime("lambda_provided")'                                                   | null      | "the Docker build strategy is LAMBDA"
        "the build adds its own entrypoint"       | true  | 'tasks.named("dockerfile") { entryPoint("java", "-jar", "app.jar") }'                    | null      | "the build adds an ENTRYPOINT"
        "the image build sets a platform"         | true  | 'tasks.named("dockerBuild") { platform = "linux/amd64" }'                                | "25-jre"  | "the image build sets a platform"
        "the training mode is set"                | true  | 'micronaut.docker.jdkAotCache.trainingMode = JdkAotCacheTrainingMode.START'              | "25-jre"  | "trainingMode or trainingPaths are set"
        "training paths are set"                  | true  | 'micronaut.docker.jdkAotCache.trainingPaths = ["/hello"]'                                | "25-jre"  | "trainingMode or trainingPaths are set"
    }

    def "enabled = false opts out"() {
        given:
        withApplication("micronaut.docker.jdkAotCache.enabled = false")
        withFakeCore(true)

        when:
        def result = build('dockerfile', 'buildLayers', '--info')

        then:
        !result.output.contains("JDK AOT cache:")
        file("build/docker/main/Dockerfile").text == DEFAULT_DOCKERFILE
        classPathOf(file("build/docker/main/layers/app/application.jar")).startsWith("resources/ classes/ libs/")
    }

    def "enabled = true keeps failing the build where the default switches itself off"() {
        given:
        withApplication("""
            micronaut.docker.jdkAotCache.enabled = true
            tasks.named("dockerfile") { jdkVersion = JavaVersion.VERSION_21 }
        """)
        withFakeCore(true)

        when:
        def result = fails('dockerfile')

        then:
        result.output.contains("The JDK AOT cache needs JDK 25 or later, but the jdkVersion of the dockerfile task is 21")
    }

    /**
     * Replaces the Micronaut core of an application set up with {@link #withApplication} with a JAR that has the
     * training run switch, and its modes if asked. The plugin only looks for the two properties in the class bytes.
     * The JAR is a module of a local repository, as the real core is, so that it goes into the layer of the
     * libraries and the configuration cache can store the layers.
     */
    private void withFakeCore(boolean mode) {
        def module = file("fake-repo/io/micronaut/micronaut-context/99.0.0")
        module.mkdirs()
        new JarOutputStream(new File(module, "micronaut-context-99.0.0.jar").newOutputStream()).withCloseable { jar ->
            jar.putNextEntry(new JarEntry("io/micronaut/runtime/Micronaut.class"))
            jar.write("io/micronaut/runtime/ApplicationConfiguration micronaut.application.training.enabled".getBytes("UTF-8"))
            jar.closeEntry()
            jar.putNextEntry(new JarEntry("io/micronaut/runtime/ApplicationConfiguration.class"))
            jar.write("micronaut.application.training.enabled ${mode ? 'micronaut.application.training.mode' : ''}".getBytes("UTF-8"))
            jar.closeEntry()
        }
        new File(module, "micronaut-context-99.0.0.pom").text = """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>io.micronaut</groupId>
  <artifactId>micronaut-context</artifactId>
  <version>99.0.0</version>
</project>
"""
        buildFile << """
            repositories {
                maven { url = file("fake-repo") }
            }

            configurations.runtimeClasspath {
                resolutionStrategy.dependencySubstitution {
                    substitute(module("io.micronaut:micronaut-context")).using(module("io.micronaut:micronaut-context:99.0.0"))
                }
            }

            dependencies {
                runtimeOnly("io.micronaut:micronaut-context")
            }
        """
    }

    private void withApplication(String configuration) {
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            import io.micronaut.gradle.docker.JdkAotCacheTrainingMode

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
