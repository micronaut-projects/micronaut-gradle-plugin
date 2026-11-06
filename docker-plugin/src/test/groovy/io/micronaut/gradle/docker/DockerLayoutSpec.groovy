package io.micronaut.gradle.docker

import io.micronaut.gradle.AbstractGradleBuildSpec
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf
import spock.lang.Issue

import java.nio.file.Files
import java.util.jar.JarFile
import java.util.zip.ZipEntry

@Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/1390")
@IgnoreIf({ os.windows })
class DockerLayoutSpec extends AbstractGradleBuildSpec {

    def "the main image packages its resources as a JAR by default"() {
        given:
        withApplication("")

        when:
        def result = build('buildLayers')

        then:
        result.task(":buildLayers").outcome == TaskOutcome.SUCCESS
        file("build/docker/main/layers/resources").list() as List == ["hello-world-runner-resources.jar"]
        entriesOf(file("build/docker/main/layers/resources/hello-world-runner-resources.jar")).contains("application.yml")
        def classPath = classPathOf(file("build/docker/main/layers/app/application.jar")).split(" ")
        classPath[0] == "resources/hello-world-runner-resources.jar"
        !classPath.contains("resources/")
        !classPath.contains("classes/")
        // The dependency JARs are copied as they are
        libs().every { jar -> Files.mismatch(jar.toPath(), cachedJar(jar).toPath()) == -1L }
    }

    def "the resources stay a directory with resourcesJar = false"() {
        given:
        withApplication("micronaut.docker.resourcesJar = false")

        when:
        build('buildLayers')

        then:
        file("build/docker/main/layers/resources/application.yml").exists()
        classPathOf(file("build/docker/main/layers/app/application.jar")).startsWith("resources/ classes/ libs/")
    }

    def "the resources stay a directory for the Lambda build strategy"() {
        given:
        withApplication("micronaut.runtime 'lambda_provided'")

        when:
        build('buildLayers')

        then:
        file("build/docker/main/layers/resources/application.yml").exists()
    }

    def "the JDK AOT cache needs the resources JAR"() {
        given:
        withApplication("""
            micronaut.docker.resourcesJar = false
            micronaut.docker.jdkAotCache.enabled = true
        """)

        when:
        def result = fails('dockerfile')

        then:
        result.output.contains("The JDK AOT cache needs the resources of the image as a JAR, but micronaut.docker.resourcesJar is false")
    }

    def "stores the entries of the dependency JARs and of the runner JARs"() {
        given:
        withApplication("micronaut.docker.storedDependencies = true")

        when:
        build('buildLayers')
        def first = libs().collectEntries { [(it.name): it.bytes] }

        then:
        !libs().isEmpty()
        libs().every { allStored(it) }
        allStored(file("build/docker/main/layers/app/application.jar"))
        allStored(file("build/docker/main/layers/resources/hello-world-runner-resources.jar"))
        libs().every { jar -> Files.getLastModifiedTime(jar.toPath()) == Files.getLastModifiedTime(cachedJar(jar).toPath()) }
        libs().every { jar -> entriesOf(jar) == entriesOf(cachedJar(jar)) }

        when: "a clean build"
        build('clean', 'buildLayers')

        then: "gives the same bytes"
        libs().every { Arrays.equals(it.bytes, first[it.name] as byte[]) }
    }

    private List<File> libs() {
        (file("build/docker/main/layers/libs").listFiles() ?: []).findAll { it.name.endsWith(".jar") }
    }

    private File cachedJar(File copy) {
        def roots = [System.getenv("GRADLE_USER_HOME"),
                     new File(System.getProperty("java.io.tmpdir"), ".gradle-test-kit").absolutePath,
                     new File(System.getProperty("user.home"), ".gradle").absolutePath].findAll { it?.trim() }
        for (String root : roots) {
            def cache = new File(root, "caches/modules-2/files-2.1")
            if (cache.exists()) {
                def found = Files.walk(cache.toPath()).withCloseable { paths ->
                    paths.filter { it.fileName.toString() == copy.name }.findFirst().orElse(null)
                }
                if (found != null) {
                    return found.toFile()
                }
            }
        }
        throw new IllegalStateException("No cached copy of ${copy.name}")
    }

    private static boolean allStored(File jar) {
        new JarFile(jar).withCloseable { it.entries().toList().every { it.method == ZipEntry.STORED } }
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

            dependencies {
                runtimeOnly("ch.qos.logback:logback-classic")
            }

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
