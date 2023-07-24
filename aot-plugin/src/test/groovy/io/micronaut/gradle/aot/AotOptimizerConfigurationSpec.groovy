package io.micronaut.gradle.aot

import io.micronaut.gradle.MicronautComponentPlugin
import org.gradle.api.plugins.JavaPlugin
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

        when:
        def t = project.tasks.getByName("prepareJitOptimizations").execute()

        then:
        Exception ex = thrown()
        baos.toString().contains("Unrecognized VM option 'ThisDoesNotExist'")

        cleanup:
        System.err = serr
    }
}
