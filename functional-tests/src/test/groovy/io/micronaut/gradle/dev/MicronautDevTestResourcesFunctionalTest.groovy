package io.micronaut.gradle.dev

import io.micronaut.gradle.fixtures.AbstractEagerConfiguringFunctionalTest
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Requires

/**
 * Development and test mode with Micronaut Test Resources: {@code mnDev} and {@code mnTest} start the test resources
 * server, or copy the settings of the project that provides it, and launch with its client and its connection
 * settings, as {@code run} and {@code test} do. The resolver is a custom one, in the {@code testResources} source set,
 * so that nothing needs Docker.
 */
@Requires({ !os.windows })
class MicronautDevTestResourcesFunctionalTest extends AbstractEagerConfiguringFunctionalTest {

    // development and test mode are in micronaut-dev 5.3: until it is released a launch needs it in the local repository
    private static final String CORE_VERSION = System.getProperty("micronautDevCoreVersion", "5.3.0-SNAPSHOT")
    private static final String GREETING = "Hello from the test resources server"

    static boolean isTestModeAvailable() {
        new File(System.getProperty("user.home"), ".m2/repository/io/micronaut/micronaut-dev-test-report/$CORE_VERSION").isDirectory()
    }

    def "mnDev and mnTest start the test resources server and launch with its client and settings"() {
        given:
        writeProject("io.micronaut.test-resources")
        writeResolver("src/testResources")

        when:
        def dryRun = build("mnDev", "mnTest", "--dry-run")

        then: "the server starts before either launch"
        dryRun.output.contains(":internalStartTestResourcesService SKIPPED")
        dryRun.output.indexOf(":internalStartTestResourcesService SKIPPED") < dryRun.output.indexOf(":mnDev SKIPPED")
        dryRun.output.indexOf(":internalStartTestResourcesService SKIPPED") < dryRun.output.indexOf(":mnTest SKIPPED")

        when:
        def inspected = build("inspectLaunches")

        then: "the connection settings are arguments of both JVMs"
        inspected.output.contains("mnDev providers: [")
        providers(inspected.output, "mnDev").contains("ServerConnectionParametersProvider")
        providers(inspected.output, "mnTest").contains("ServerConnectionParametersProvider")

        when:
        def manifests = build("mnDevManifest", "mnTestManifest")

        then: "the client is a module of both launches, in the parent tier"
        manifests.task(":mnDevManifest").outcome == TaskOutcome.SUCCESS
        file("build/micronaut-dev/runtime.argfile").readLines().any { it.contains("micronaut-test-resources-client-") }
        file("build/micronaut-dev/test/runtime.argfile").readLines().any { it.contains("micronaut-test-resources-client-") }
        inspected.output.contains("mnDev launches the client: true")
        inspected.output.contains("mnTest launches the client: true")

        when: "the configuration cache stores the manifests: storing the launches would store the compile tasks, which the JDK these tests run on may refuse"
        def stored = build("mnDevManifest", "mnTestManifest", "--configuration-cache")
        def reused = build("mnDevManifest", "mnTestManifest", "--configuration-cache")

        then:
        stored.output.contains("Configuration cache entry stored")
        reused.output.contains("Reusing configuration cache")
    }

    def "mnDev and mnTest of a consumer use the server of the project that provides it"() {
        given:
        writeConsumerBuild()

        when:
        def dryRun = build(":app:mnDev", ":app:mnTest", "--dry-run")

        then: "the provider's server starts and its settings are copied before either launch"
        dryRun.output.contains(":testresources:internalStartTestResourcesService SKIPPED")
        dryRun.output.contains(":app:copyTestResourceServerConfig SKIPPED")
        dryRun.output.indexOf(":app:copyTestResourceServerConfig SKIPPED") < dryRun.output.indexOf(":app:mnDev SKIPPED")
        dryRun.output.indexOf(":app:copyTestResourceServerConfig SKIPPED") < dryRun.output.indexOf(":app:mnTest SKIPPED")

        when:
        def inspected = build(":app:inspectLaunches")

        then:
        providers(inspected.output, "mnDev").contains("ServerConnectionParametersProvider")
        providers(inspected.output, "mnTest").contains("ServerConnectionParametersProvider")
        inspected.output.contains("mnDev launches the client: true")
        inspected.output.contains("mnTest launches the client: true")

        when:
        build(":app:mnDevManifest", ":app:mnTestManifest")
        def dev = manifest("app/build/micronaut-dev/dev.properties")
        def test = manifest("app/build/micronaut-dev/test/dev.properties")

        then: "the provider is not a project of the application: neither its sources nor its settings are reloadable"
        // the temporary directory may be named through a link: either name of the provider's directory
        def provider = [file("testresources").absolutePath, file("testresources").canonicalPath]
        [dev, test].every { m -> provider.every { !m."micronaut.dev.sources.java".contains(it) } }
        [dev, test].every { m -> provider.every { !m."micronaut.dev.reloadable".contains(it) } }
        [dev, test].every { m -> m."micronaut.dev.reloadable".split(File.pathSeparator).size() == (m.is(dev) ? 2 : 4) }
        file("app/build/micronaut-dev/runtime.argfile").readLines().any { it.contains("micronaut-test-resources-client-") }
        file("app/build/micronaut-dev/test/runtime.argfile").readLines().any { it.contains("micronaut-test-resources-client-") }
        file("app/build/micronaut-dev/test/runtime.argfile").readLines().every { line -> provider.every { !line.contains(it) } }

        when:
        def stored = build(":app:mnDevManifest", ":app:mnTestManifest", "--configuration-cache")
        def reused = build(":app:mnDevManifest", ":app:mnTestManifest", "--configuration-cache")

        then:
        stored.output.contains("Configuration cache entry stored")
        reused.output.contains("Reusing configuration cache")
    }

    @Requires({ isTestModeAvailable() })
    def "a test run by mnTest and the application run by mnDev read a property the server resolves"() {
        given:
        allowMavenLocal = true
        writeProject("io.micronaut.test-resources", true)
        writeResolver("src/testResources")

        when:
        def tested = build("mnTest", "--once", "-S")

        then:
        tested.task(":internalStartTestResourcesService").outcome == TaskOutcome.SUCCESS
        tested.task(":mnTest").outcome == TaskOutcome.SUCCESS
        def report = file("build/test-results/mnTest/TEST-example.GreetingTest.xml")
        report.exists()
        report.text.contains('tests="1"')
        !report.text.contains("<failure")

        when: "the application, which prints the property and exits"
        def developed = build("mnDev", "-S")

        then:
        developed.task(":mnDev").outcome == TaskOutcome.SUCCESS
        developed.output.contains("greeting=" + GREETING)
    }

    @Requires({ isTestModeAvailable() })
    def "a test of a consumer run by mnTest reads a property the provider's server resolves"() {
        given:
        allowMavenLocal = true
        writeConsumerBuild(true)

        when:
        def tested = build(":app:mnTest", "--once", "-S")

        then:
        tested.task(":testresources:internalStartTestResourcesService").outcome == TaskOutcome.SUCCESS
        tested.task(":app:mnTest").outcome == TaskOutcome.SUCCESS
        def report = file("app/build/test-results/mnTest/TEST-example.GreetingTest.xml")
        report.exists()
        !report.text.contains("<failure")

        when:
        def developed = build(":app:mnDev", "-S")

        then:
        developed.task(":app:mnDev").outcome == TaskOutcome.SUCCESS
        developed.output.contains("greeting=" + GREETING)
    }

    private void writeConsumerBuild(boolean launch = false) {
        settingsFile << """
            rootProject.name = 'consumer'
            include 'app'
            include 'testresources'
        """
        file("testresources").mkdirs()
        file("testresources/build.gradle") << """
            plugins {
                id "io.micronaut.test-resources"
            }
            micronaut {
                version "$micronautVersion"
            }
            $repositoriesBlock
        """
        writeResolver("testresources/src/testResources")
        writeProject("io.micronaut.test-resources-consumer", launch, "app/", """
            dependencies {
                testResourcesService(project(":testresources"))
            }
        """)
    }

    private void writeProject(String testResourcesPlugin, boolean launch = false, String directory = "", String extra = "") {
        if (!directory) {
            settingsFile << "rootProject.name = 'hello-world'"
        }
        File build = directory ? file(directory + "build.gradle") : buildFile
        build.parentFile.mkdirs()
        build << """
            plugins {
                id "io.micronaut.application"
                id "$testResourcesPlugin"
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
            }
            $repositoriesBlock
            ${launch ? """
            dependencies {
                mnDevRuntime(platform("io.micronaut:micronaut-core-bom:$CORE_VERSION"))
                mnTestRuntime(platform("io.micronaut:micronaut-core-bom:$CORE_VERSION"))
            }""" : ""}
            application { mainClass = "example.Application" }
            $extra

            // what the launches are given, without resolving their classpaths, which need micronaut-dev: the configuration
            // cache is not used here
            tasks.register("inspectLaunches") {
                doLast {
                    ["mnDev": "mnDevRuntime", "mnTest": "mnTestRuntime"].each { name, launch ->
                        def task = tasks.getByName(name)
                        println "\$name providers: \${task.jvmArgumentProviders.collect { it.class.simpleName }}"
                        // the client, or the provider's settings with the client, among what the launch configuration extends
                        def client = configurations.getByName(launch).hierarchy.any { it.name in ["testResourcesClient", "testResourcesService"] }
                        println "\$name launches the client: \$client"
                    }
                }
            }
        """
        writeSource(directory + "src/main/java/example/Application.java", """
            package example;
            import io.micronaut.context.ApplicationContext;
            public class Application {
                public static void main(String[] args) {
                    try (ApplicationContext context = ApplicationContext.run()) {
                        System.out.println("greeting=" + context.getBean(Greeter.class).greeting());
                    }
                    // the launcher would watch the sources until stopped: the application ends the run
                    System.exit(0);
                }
            }
        """)
        writeSource(directory + "src/main/java/example/Greeter.java", """
            package example;
            import io.micronaut.context.annotation.Value;
            import jakarta.inject.Singleton;
            @Singleton
            public class Greeter {
                private final String greeting;
                public Greeter(@Value("\${greeting.message}") String greeting) {
                    this.greeting = greeting;
                }
                public String greeting() {
                    return greeting;
                }
            }
        """)
        writeSource(directory + "src/test/java/example/GreetingTest.java", """
            package example;
            import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
            import jakarta.inject.Inject;
            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.assertEquals;
            @MicronautTest(startApplication = false)
            class GreetingTest {
                @Inject
                Greeter greeter;
                @Test
                void greets() {
                    assertEquals("$GREETING", greeter.greeting());
                }
            }
        """)
    }

    private void writeResolver(String sourceSet) {
        writeSource("$sourceSet/java/example/GreetingTestResource.java", """
            package example;
            import io.micronaut.testresources.core.TestResourcesResolver;
            import java.util.Collection;
            import java.util.List;
            import java.util.Map;
            import java.util.Optional;
            public class GreetingTestResource implements TestResourcesResolver {
                private static final String PROPERTY = "greeting.message";
                @Override
                public List<String> getResolvableProperties(Map<String, Collection<String>> propertyEntries, Map<String, Object> testResourcesConfig) {
                    return List.of(PROPERTY);
                }
                @Override
                public Optional<String> resolve(String propertyName, Map<String, Object> properties, Map<String, Object> testResourcesConfiguration) {
                    return PROPERTY.equals(propertyName) ? Optional.of("$GREETING") : Optional.empty();
                }
            }
        """)
        writeSource("$sourceSet/resources/META-INF/services/io.micronaut.testresources.core.TestResourcesResolver", "example.GreetingTestResource\n")
    }

    private static String providers(String output, String task) {
        output.readLines().find { it.startsWith(task + " providers: ") } ?: ""
    }

    private void writeSource(String path, String source) {
        File file = file(path)
        file.parentFile.mkdirs()
        file << source.stripIndent()
    }

    private Properties manifest(String path) {
        Properties properties = new Properties()
        file(path).withInputStream { properties.load(it) }
        properties
    }
}
