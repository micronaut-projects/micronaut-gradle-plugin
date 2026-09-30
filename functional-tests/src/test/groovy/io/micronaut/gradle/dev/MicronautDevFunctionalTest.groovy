package io.micronaut.gradle.dev

import io.micronaut.gradle.fixtures.AbstractEagerConfiguringFunctionalTest
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Shared

class MicronautDevFunctionalTest extends AbstractEagerConfiguringFunctionalTest {

    @Shared
    private final String kotlin2Version = System.getProperty("kotlin2Version")
    @Shared
    private final String ksp2Version = System.getProperty("ksp2Version")

    def "the manifest describes a Java project and the mnDev task is registered"() {
        given:
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
                    strategy = "restart"
                    retain("javax.sql.DataSource")
                    liveReload {
                        port = 35730
                        injectScript = false
                    }
                }
            }
            $repositoriesBlock
            application { mainClass = "example.Application" }
        """
        writeApplication("java", "src/main/java/example/Application.java", """
            package example;
            public class Application {
                public static void main(String[] args) { }
            }
        """)
        file("src/main/resources/static").mkdirs()
        file("src/main/resources/application.yml") << "micronaut:\n  application:\n    name: hello\n"
        file("src/main/resources/static/app.css") << "body {}"

        when:
        def result = buildManifest()

        then:
        result.task(":mnDevManifest").outcome == TaskOutcome.SUCCESS
        def manifest = manifest()
        manifest."micronaut.dev.main-class" == "example.Application"
        manifest."micronaut.dev.strategy" == "restart"
        manifest."micronaut.dev.compile.mode" == "embedded"
        manifest."micronaut.dev.compile.incremental" == "true"
        manifest."micronaut.dev.build-tool" == "gradle"
        manifest."micronaut.dev.build-tool.trigger".endsWith("micronaut-dev" + File.separator + "reload")
        manifest."micronaut.dev.retain" == "javax.sql.DataSource"
        manifest."micronaut.dev.livereload.port" == "35730"
        manifest."micronaut.dev.livereload.inject-script" == "false"
        manifest."micronaut.dev.sources.java".endsWith("src" + File.separator + "main" + File.separator + "java")
        manifest."micronaut.dev.resources.config".endsWith("src" + File.separator + "main" + File.separator + "resources")
        manifest."micronaut.dev.resources.static".endsWith("static")
        manifest."micronaut.dev.compile.java.output".endsWith("build" + File.separator + "classes" + File.separator + "java" + File.separator + "main")
        manifest."micronaut.dev.compile.java.generated-sources".contains("annotationProcessor")
        manifest."micronaut.dev.compile.java.options" == "@java-options.argfile"
        file("build/micronaut-dev/java-options.argfile").readLines().containsAll(["-parameters", "-Amicronaut.processing.group=example", "-Amicronaut.processing.module=hello-world"])

        and: "the reloadable tier is the project's outputs, the parent tier the modules"
        manifest."micronaut.dev.reloadable".contains("classes" + File.separator + "java" + File.separator + "main")
        manifest."micronaut.dev.reloadable".contains("resources" + File.separator + "main")
        manifest."micronaut.dev.runtime-classpath" == "@runtime.argfile"
        def runtime = file("build/micronaut-dev/runtime.argfile").readLines()
        runtime.any { it.contains("micronaut-runtime-") }
        runtime.every { !it.contains("hello-world") }
        manifest."micronaut.dev.processor-path" == "@processors.argfile"
        file("build/micronaut-dev/processors.argfile").readLines().any { it.contains("micronaut-inject-java") }
        file("build/micronaut-dev/compile.argfile").readLines().any { it.contains("micronaut-inject-") }

        when:
        def tasks = build("tasks", "--all")

        then:
        tasks.output.contains("mnDev - Runs the application in development mode")

        when: "the configuration cache stores the manifest task and reuses it"
        file("build/micronaut-dev").deleteDir()
        def stored = build("mnDevManifest", "--configuration-cache")
        def reused = build("mnDevManifest", "--configuration-cache")

        then:
        stored.output.contains("Configuration cache entry stored")
        reused.output.contains("Reusing configuration cache")
        reused.task(":mnDevManifest").outcome in [TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE]
        this.manifest()."micronaut.dev.main-class" == "example.Application"
    }

    def "the sources of the projects depended on are listed with the application's"() {
        given:
        settingsFile << """
            rootProject.name = 'hello-world'
            include 'lib'
            include 'common'
        """
        buildFile << """
            plugins {
                id "io.micronaut.application"
            }
            micronaut {
                version "$micronautVersion"
                runtime "netty"
            }
            $repositoriesBlock
            dependencies {
                implementation(project(":lib"))
            }
            application { mainClass = "example.Application" }
        """
        file("lib").mkdirs()
        file("lib/build.gradle") << """
            plugins { id "java-library" }
            $repositoriesBlock
            dependencies { api(project(":common")) }
        """
        file("common").mkdirs()
        file("common/build.gradle") << """
            plugins { id "java-library" }
            $repositoriesBlock
        """
        writeApplication("java", "src/main/java/example/Application.java", """
            package example;
            public class Application {
                public static void main(String[] args) { }
            }
        """)
        writeApplication("java", "lib/src/main/java/lib/Greeter.java", "package lib; public class Greeter { }")
        writeApplication("java", "common/src/main/java/common/Greeting.java", "package common; public class Greeting { }")
        file("common/src/main/resources").mkdirs()
        file("common/src/main/resources/common.properties") << "a=b"

        when:
        def result = buildManifest()

        then: "the application's own sources and those of lib and, through it, common"
        result.task(":mnDevManifest").outcome == TaskOutcome.SUCCESS
        def manifest = manifest()
        def sources = manifest."micronaut.dev.sources.java".split(File.pathSeparator).toList()
        sources.size() == 3
        sources.any { it.endsWith("lib" + File.separator + "src" + File.separator + "main" + File.separator + "java") }
        sources.any { it.endsWith("common" + File.separator + "src" + File.separator + "main" + File.separator + "java") }
        manifest."micronaut.dev.resources.config".contains("common" + File.separator + "src" + File.separator + "main" + File.separator + "resources")
        def reloadable = manifest."micronaut.dev.reloadable".split(File.pathSeparator).toList()
        reloadable[0].endsWith("classes" + File.separator + "java" + File.separator + "main")
        !reloadable[0].contains("lib" + File.separator)
        reloadable.any { it.contains("lib" + File.separator + "build") }
        reloadable.any { it.contains("common" + File.separator + "build") }
    }

    def "in build-tool mode the classes task touches the trigger the launcher watches"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.application"
            }
            micronaut {
                version "$micronautVersion"
                runtime "netty"
                dev {
                    compile = "build-tool"
                }
            }
            $repositoriesBlock
            application { mainClass = "example.Application" }
        """
        writeApplication("java", "src/main/java/example/Application.java", """
            package example;
            public class Application {
                public static void main(String[] args) { }
            }
        """)

        when:
        def result = build("classes")

        then:
        result.task(":mnDevTrigger").outcome == TaskOutcome.SUCCESS
        file("build/micronaut-dev/reload").exists()

        when: "embedded, the trigger is not touched"
        buildFile.text = buildFile.text.replace('compile = "build-tool"', 'compile = "embedded"')
        file("build/micronaut-dev/reload").delete()
        result = build("classes")

        then:
        result.task(":mnDevTrigger").outcome == TaskOutcome.SKIPPED
        !file("build/micronaut-dev/reload").exists()
    }

    def "the manifest describes a Kotlin project with KSP and the compilers join mnDevCompiler"() {
        given:
        settingsFile << "rootProject.name = 'hello-kotlin'"
        buildFile << """
            plugins {
                id("org.jetbrains.kotlin.jvm") version "$kotlin2Version"
                id("com.google.devtools.ksp") version "$ksp2Version"
                id("org.jetbrains.kotlin.plugin.allopen") version "$kotlin2Version"
                id "io.micronaut.application"
            }
            group = "example"
            micronaut {
                version "$micronautVersion"
                runtime "netty"
            }
            $repositoriesBlock
            application { mainClass = "example.Application" }
        """
        writeApplication("kotlin", "src/main/kotlin/example/Application.kt", """
            package example
            object Application {
                @JvmStatic fun main(args: Array<String>) { }
            }
        """)

        when:
        def result = buildManifest("dependencies", "--configuration", "mnDevCompiler")

        then:
        result.task(":mnDevManifest")?.outcome == TaskOutcome.SUCCESS
        def manifest = manifest()
        manifest."micronaut.dev.sources.kotlin".endsWith("src" + File.separator + "main" + File.separator + "kotlin")
        manifest."micronaut.dev.compile.kotlin.output".endsWith("kotlin" + File.separator + "main")
        !manifest.containsKey("micronaut.dev.compile.kotlin.mode")
        manifest."micronaut.dev.compile.kotlin.options" == "@kotlin-options.argfile"
        def options = file("build/micronaut-dev/kotlin-options.argfile").readLines()
        options.contains("-java-parameters")
        options.any { it.startsWith("-Xplugin=") && it.contains("allopen") }
        options.contains("plugin:org.jetbrains.kotlin.allopen:annotation=io.micronaut.aop.Around")
        file("build/micronaut-dev/processors.argfile").readLines().any { it.contains("micronaut-inject-kotlin") }
        result.output.contains("org.jetbrains.kotlin:kotlin-build-tools-impl:$kotlin2Version")
        result.output.contains("com.google.devtools.ksp:symbol-processing-aa-embeddable:$ksp2Version")
    }

    def "a KAPT project has its Kotlin compiled by Gradle"() {
        given:
        settingsFile << "rootProject.name = 'hello-kapt'"
        buildFile << """
            plugins {
                id("org.jetbrains.kotlin.jvm") version "$kotlin2Version"
                id("org.jetbrains.kotlin.kapt") version "$kotlin2Version"
                id "io.micronaut.application"
            }
            micronaut {
                version "$micronautVersion"
                runtime "netty"
            }
            $repositoriesBlock
            application { mainClass = "example.Application" }
        """
        writeApplication("kotlin", "src/main/kotlin/example/Application.kt", """
            package example
            object Application {
                @JvmStatic fun main(args: Array<String>) { }
            }
        """)

        when:
        def result = buildManifest()

        then:
        result.task(":mnDevManifest").outcome == TaskOutcome.SUCCESS
        manifest()."micronaut.dev.compile.kotlin.mode" == "build-tool"
    }

    private def buildManifest(String... args) {
        def result = build("mnDevManifest", *args)
        if (result.task(":mnDevManifest")?.outcome != org.gradle.testkit.runner.TaskOutcome.SUCCESS) {
            println "MANIFEST TASK MISSING; tasks=" + result.tasks*.path + "\n" + result.output
        }
        result
    }

    private void writeApplication(String language, String path, String source) {
        File file = file(path)
        file.parentFile.mkdirs()
        file << source.stripIndent()
    }

    private Properties manifest() {
        Properties properties = new Properties()
        file("build/micronaut-dev/dev.properties").withInputStream { properties.load(it) }
        properties
    }
}
