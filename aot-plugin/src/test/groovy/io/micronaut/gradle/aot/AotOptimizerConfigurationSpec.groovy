package io.micronaut.gradle.aot

import io.micronaut.gradle.MicronautComponentPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class AotOptimizerConfigurationSpec extends Specification {
    def "can configure JVM arguments"() {
        def serr = System.err
        given:
        def baos = new ByteArrayOutputStream()
        System.err = new PrintStream(baos)
        def project = ProjectBuilder.builder()
                .build()

        String micronautVersion = System.getProperty("micronautVersion")
        project.extensions.add('micronautVersion', micronautVersion)
        project.pluginManager.apply(JavaPlugin)
        project.pluginManager.apply(MicronautComponentPlugin)
        project.pluginManager.apply(MicronautAotPlugin)
        project.repositories.mavenCentral()
        project.tasks.withType(AbstractMicronautAotCliTask).configureEach {
            jvmArgs.add('-XX:ThisDoesNotExist')
        }
        project.dependencies.add("aotApplicationClasspath", "io.micronaut.platform:micronaut-platform:$micronautVersion")
        // Ensure afterEvaluate hooks have run before executing tasks.
        project.evaluate()

        when:
        def t = project.tasks.getByName("prepareJitOptimizations").execute()

        then:
        Exception ex = thrown()
        def combined = new StringBuilder(baos.toString())
        Throwable current = ex
        while (current != null) {
            if (current.message) {
                combined.append("\n").append(current.message)
            }
            current = current.cause
        }
        combined.toString().contains("ThisDoesNotExist") || combined.toString().contains("Unrecognized VM option")

        cleanup:
        System.err = serr
    }

    def "AOT task uses configured Java toolchain"() {
        given:
        def project = ProjectBuilder.builder().build()

        String micronautVersion = System.getProperty("micronautVersion")
        assert micronautVersion != null && !micronautVersion.empty : "micronautVersion must be set"
        project.extensions.add('micronautVersion', micronautVersion)

        project.pluginManager.apply(JavaPlugin)
        project.pluginManager.apply(MicronautComponentPlugin)
        project.pluginManager.apply(MicronautAotPlugin)
        project.repositories.mavenCentral()
        project.dependencies.add("aotApplicationClasspath", "io.micronaut.platform:micronaut-platform:$micronautVersion")

        def javaExtension = project.extensions.getByType(JavaPluginExtension)
        javaExtension.toolchain.languageVersion.set(JavaLanguageVersion.of(1024))

        def toolchainService = project.extensions.getByType(JavaToolchainService)
        def invalidLauncher = toolchainService.launcherFor(javaExtension.toolchain)

        when:
        def aotTask = project.tasks.getByName("prepareJitOptimizations") as AbstractMicronautAotCliTask
        aotTask.javaLauncher.set(invalidLauncher)
        project.gradle.startParameter.taskNames = ["prepareJitOptimizations"]
        aotTask.actions.forEach { action ->
            action.execute(aotTask)
        }


        then:
        def ex = thrown(Exception)
        ex.message != null && (ex.message.toString().contains("Failed to query the value of task ':prepareJitOptimizations' property 'javaLauncher'"))
    }

        def "AOT task falls back to default when no toolchain configured"() {
        given:
        def project = ProjectBuilder.builder().build()

        String micronautVersion = System.getProperty("micronautVersion")
        project.extensions.add('micronautVersion', micronautVersion)

        project.pluginManager.apply(JavaPlugin)
        project.pluginManager.apply(MicronautComponentPlugin)
        project.pluginManager.apply(MicronautAotPlugin)
        project.repositories.mavenCentral()

        project.dependencies.add("aotApplicationClasspath", "io.micronaut.platform:micronaut-platform:$micronautVersion")

        when:
        def aotTask = project.tasks.getByName("prepareJitOptimizations") as AbstractMicronautAotCliTask

        then:
        !aotTask.javaLauncher.isPresent()
    }
}
