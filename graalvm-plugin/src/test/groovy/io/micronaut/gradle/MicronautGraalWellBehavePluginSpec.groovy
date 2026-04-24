package io.micronaut.gradle

import io.micronaut.gradle.graalvm.GraalUtil
import org.graalvm.buildtools.gradle.dsl.GraalVMExtension
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class MicronautGraalWellBehavePluginSpec extends Specification {
    def "graal processor is added with plugin #plugin"() {
        def project = ProjectBuilder.builder().build()

        when:
        project.plugins.apply("io.micronaut.graalvm")
        project.plugins.apply (plugin)

        then:
        project.configurations.annotationProcessor.dependencies.find { it.name == 'micronaut-graal' }

        where:
        plugin << [
                "io.micronaut.minimal.library",
                "io.micronaut.minimal.application",
        ]
    }

    def "shared arena support is enabled by default"() {
        def project = ProjectBuilder.builder().build()

        when:
        project.plugins.apply("io.micronaut.graalvm")
        project.plugins.apply("io.micronaut.minimal.application")
        def graal = project.extensions.getByType(GraalVMExtension)

        then:
        graal.binaries.getByName("main").buildArgs.get().contains("-H:+SharedArenaSupport") == GraalUtil.supportsSharedArenaSupport(GraalUtil.currentJavaMajorVersion())
    }

    def "shared arena support utility tracks supported Java versions"() {
        expect:
        !GraalUtil.supportsSharedArenaSupport(21)
        !GraalUtil.supportsSharedArenaSupport(24)
        GraalUtil.supportsSharedArenaSupport(25)
    }
}
