package io.micronaut.gradle.dev

import io.micronaut.gradle.fixtures.AbstractEagerConfiguringFunctionalTest
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Requires
import spock.lang.Shared

class MicronautTestModeFunctionalTest extends AbstractEagerConfiguringFunctionalTest {

    // test mode is in micronaut-dev 5.3: until it is released the run needs it in the local repository, and is skipped without
    private static final String CORE_VERSION = System.getProperty("micronautDevCoreVersion", "5.3.0-SNAPSHOT")

    @Shared
    private final String kotlin2Version = System.getProperty("kotlin2Version")
    @Shared
    private final String ksp2Version = System.getProperty("ksp2Version")

    static boolean isTestModeAvailable() {
        new File(System.getProperty("user.home"), ".m2/repository/io/micronaut/micronaut-dev-test-report/$CORE_VERSION").isDirectory()
    }

    def "the manifest of test mode describes the tests and the mnTest task is registered"() {
        given:
        writeProject("""
            dev {
                test {
                    selection = "all"
                    initialRun = false
                    reportPath = "/reports/tests/"
                    parameters.put("junit.jupiter.execution.parallel.enabled", "false")
                }
            }
        """)

        when:
        def result = build("mnTestManifest")

        then:
        result.task(":mnTestManifest").outcome == TaskOutcome.SUCCESS
        def manifest = manifest()
        manifest."micronaut.dev.mode" == "test"
        manifest."micronaut.dev.main-class" == "example.Application"
        manifest."micronaut.dev.sources.java".endsWith(path("src", "main", "java"))
        manifest."micronaut.dev.test.sources.java".endsWith(path("src", "test", "java"))
        manifest."micronaut.dev.test.resources.config".endsWith(path("src", "test", "resources"))
        manifest."micronaut.dev.compile.java.output".endsWith(path("build", "classes", "java", "main"))
        manifest."micronaut.dev.test.compile.java.output".endsWith(path("build", "classes", "java", "test"))
        manifest."micronaut.dev.test.compile.java.options" == "@test-java-options.argfile"
        manifest."micronaut.dev.test.compile-classpath" == "@test-compile.argfile"
        manifest."micronaut.dev.test.processor-path" == "@test-processors.argfile"
        manifest."micronaut.dev.test.runner" == "junit-platform"
        manifest."micronaut.dev.test.selection" == "all"
        manifest."micronaut.dev.test.initial-run" == "false"
        manifest."micronaut.dev.test.html-report-path" == "/reports/tests/"
        manifest."micronaut.dev.test.parameters.junit.jupiter.execution.parallel.enabled" == "false"
        manifest."micronaut.dev.test.reports".endsWith(path("build", "test-results", "mnTest"))
        manifest."micronaut.dev.test.html-report".endsWith(path("build", "reports", "tests", "mnTest"))
        file("build/micronaut-dev/test/test-compile.argfile").readLines().any { it.contains("junit-jupiter-api") }
        file("build/micronaut-dev/test/test-processors.argfile").readLines().any { it.contains("micronaut-inject-java") }

        and: "the tests' outputs are reloadable ahead of the application's, and the run-mode manifest is left alone"
        def reloadable = manifest."micronaut.dev.reloadable".split(File.pathSeparator).toList()
        reloadable.indexOf(reloadable.find { it.endsWith(path("classes", "java", "test")) }) < reloadable.indexOf(reloadable.find { it.endsWith(path("classes", "java", "main")) })
        reloadable.any { it.endsWith(path("resources", "test")) }
        !file("build/micronaut-dev/dev.properties").exists()

        when:
        def tasks = build("tasks", "--all")

        then:
        tasks.output.contains("mnTest - Runs the tests in test mode")

        when: "the configuration cache stores the manifest task and reuses it"
        file("build/micronaut-dev").deleteDir()
        def stored = build("mnTestManifest", "--configuration-cache")
        def reused = build("mnTestManifest", "--configuration-cache")

        then:
        stored.output.contains("Configuration cache entry stored")
        reused.output.contains("Reusing configuration cache")
        this.manifest()."micronaut.dev.mode" == "test"
    }

    @Requires({ isTestModeAvailable() })
    def "mnTest runs the tests once, fails with a failing test, and passes once the application is fixed"() {
        given:
        allowMavenLocal = true
        writeProject()
        writeCalculator("a - b")

        when:
        def failed = fails("mnTest", "--once")

        then:
        failed.task(":mnTest").outcome == TaskOutcome.FAILED
        def failure = file("build/test-results/mnTest/TEST-example.CalculatorTest.xml")
        failure.text.contains("<failure")
        !file("build/test-results/mnTest/TEST-example.GreetingTest.xml").text.contains("<failure")

        when: "only the passing test is selected"
        def filtered = build("mnTest", "--once", "--tests", "example.GreetingTest")

        then:
        filtered.task(":mnTest").outcome == TaskOutcome.SUCCESS

        when: "the application is fixed"
        writeCalculator("a + b")
        def passed = build("mnTest", "--once")

        then:
        passed.task(":mnTest").outcome == TaskOutcome.SUCCESS
        !file("build/test-results/mnTest/TEST-example.CalculatorTest.xml").text.contains("<failure")
        file("build/test-results/mnTest/TEST-example.GreetingTest.xml").exists()
        file("build/reports/tests/mnTest/index.html").exists()
    }

    def "the manifest of test mode describes Kotlin tests with KSP"() {
        given:
        settingsFile << "rootProject.name = 'hello-kotlin'"
        buildFile << """
            plugins {
                id("org.jetbrains.kotlin.jvm") version "$kotlin2Version"
                id("com.google.devtools.ksp") version "$ksp2Version"
                id "io.micronaut.application"
            }
            micronaut {
                version "$micronautVersion"
                runtime "netty"
                testRuntime "junit5"
            }
            $repositoriesBlock
            application { mainClass = "example.Application" }
        """
        writeSource("src/main/kotlin/example/Application.kt", """
            package example
            object Application {
                @JvmStatic fun main(args: Array<String>) { }
            }
        """)
        writeSource("src/test/kotlin/example/ApplicationTest.kt", """
            package example
            import org.junit.jupiter.api.Test
            class ApplicationTest {
                @Test fun runs() { }
            }
        """)

        when:
        def result = build("mnTestManifest")

        then:
        result.task(":mnTestManifest").outcome == TaskOutcome.SUCCESS
        def manifest = manifest()
        manifest."micronaut.dev.sources.kotlin".endsWith(path("src", "main", "kotlin"))
        manifest."micronaut.dev.test.sources.kotlin".endsWith(path("src", "test", "kotlin"))
        manifest."micronaut.dev.compile.kotlin.output".endsWith(path("kotlin", "main"))
        manifest."micronaut.dev.test.compile.kotlin.output".endsWith(path("kotlin", "test"))
        manifest."micronaut.dev.test.compile.kotlin.options" == "@test-kotlin-options.argfile"
        file("build/micronaut-dev/test/test-kotlin-options.argfile").readLines().contains("-java-parameters")
        manifest."micronaut.dev.test.html-report-path" == "/tests/"
        file("build/micronaut-dev/test/test-processors.argfile").readLines().any { it.contains("micronaut-inject-kotlin") }
    }

    private void writeProject(String dev = "") {
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.application"
            }
            group = "example"
            micronaut {
                version "$micronautVersion"
                runtime "netty"
                testRuntime "junit5"
                dev {
                    // no server on the default port, which a running mnDev may hold
                    liveReload {
                        enabled = false
                    }
                }
                $dev
            }
            $repositoriesBlock
            dependencies {
                mnTestRuntime(platform("io.micronaut:micronaut-core-bom:$CORE_VERSION"))
            }
            application { mainClass = "example.Application" }
        """
        writeSource("src/main/java/example/Application.java", """
            package example;
            public class Application {
                public static void main(String[] args) { }
            }
        """)
        writeSource("src/test/java/example/GreetingTest.java", """
            package example;
            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.assertEquals;
            class GreetingTest {
                @Test
                void greets() {
                    assertEquals("hello", "hel" + "lo");
                }
            }
        """)
        writeSource("src/test/java/example/CalculatorTest.java", """
            package example;
            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.assertEquals;
            class CalculatorTest {
                @Test
                void adds() {
                    assertEquals(3, Calculator.add(1, 2));
                }
            }
        """)
        writeCalculator("a + b")
        file("src/test/resources").mkdirs()
        file("src/test/resources/application-test.yml") << "micronaut:\n  application:\n    name: hello-test\n"
    }

    private void writeCalculator(String expression) {
        File calculator = file("src/main/java/example/Calculator.java")
        calculator.parentFile.mkdirs()
        calculator.text = """
            package example;
            public class Calculator {
                public static int add(int a, int b) {
                    return $expression;
                }
            }
        """.stripIndent()
    }

    private void writeSource(String path, String source) {
        File file = file(path)
        file.parentFile.mkdirs()
        file << source.stripIndent()
    }

    private static String path(String... segments) {
        segments.join(File.separator)
    }

    private Properties manifest() {
        Properties properties = new Properties()
        file("build/micronaut-dev/test/dev.properties").withInputStream { properties.load(it) }
        properties
    }
}
