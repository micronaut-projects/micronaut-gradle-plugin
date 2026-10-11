package io.micronaut.gradle.dev

import org.gradle.api.artifacts.Dependency
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class MicronautDevControlPanelSpec extends Specification {

    def "the panels follow the modules the application uses"() {
        expect:
        MicronautDevControlPanel.Panel.detect(modules) == (panels as Set)

        where:
        modules                                                         | panels
        ["io.micronaut:micronaut-http-server-netty"]                    | []
        ["io.micronaut.sql:micronaut-jdbc"]                             | [MicronautDevControlPanel.Panel.DATASOURCE]
        ["org.hibernate.orm:hibernate-core"]                            | [MicronautDevControlPanel.Panel.HIBERNATE]
        ["io.micronaut.kafka:micronaut-kafka"]                          | [MicronautDevControlPanel.Panel.KAFKA]
        ["io.micronaut.objectstorage:micronaut-object-storage-core"]    | [MicronautDevControlPanel.Panel.OBJECT_STORAGE]
        ["io.micronaut.cache:micronaut-cache-caffeine"]                 | [MicronautDevControlPanel.Panel.CACHE]
        ["io.micronaut.cache:micronaut-cache-ehcache"]                  | [MicronautDevControlPanel.Panel.CACHE]
        ["io.micronaut.cache:micronaut-cache-hazelcast"]                | [MicronautDevControlPanel.Panel.CACHE]
        ["io.micronaut.cache:micronaut-cache-infinispan"]               | [MicronautDevControlPanel.Panel.CACHE]
        ["io.micronaut.sql:micronaut-jdbc", "io.micronaut.kafka:micronaut-kafka"] | [MicronautDevControlPanel.Panel.DATASOURCE, MicronautDevControlPanel.Panel.KAFKA]
        // a module whose name only starts with that of a trigger does not match
        ["io.micronaut.sql:micronaut-jdbc-hikari"]                      | []
        // nor one of the same name in another group
        ["example:micronaut-kafka"]                                     | []
    }

    def "the base modules come first, then the panels"() {
        expect:
        MicronautDevControlPanel.modules(["io.micronaut.kafka:micronaut-kafka"]) == [
            "io.micronaut.controlpanel:micronaut-control-panel-ui",
            "io.micronaut.controlpanel:micronaut-control-panel-management",
            "io.micronaut.controlpanel:micronaut-control-panel-testing",
            "io.micronaut.controlpanel:micronaut-control-panel-kafka"
        ]
        MicronautDevControlPanel.modules([]) == MicronautDevControlPanel.BASE_MODULES
    }

    def "the defaults are those of a development launch"() {
        expect:
        MicronautDevControlPanel.DEFAULTS == [
            "micronaut.control-panel.enabled"        : "true",
            "micronaut.control-panel.path"           : "/control-panel",
            "micronaut.control-panel.security.access": "ANONYMOUS"
        ]
        MicronautDevControlPanel.defaults(["io.micronaut:micronaut-http-server-netty"]) == MicronautDevControlPanel.DEFAULTS
    }

    def "with Micronaut Security the defaults leave the access to the application's security"() {
        when:
        def defaults = MicronautDevControlPanel.defaults(["io.micronaut:micronaut-http-server-netty", "io.micronaut.security:micronaut-security"])

        then:
        defaults == [
            "micronaut.control-panel.enabled": "true",
            "micronaut.control-panel.path"   : "/control-panel"
        ]
        !defaults.containsKey("micronaut.control-panel.security.access")
    }

    def "the dependencies are inferred from the resolved classpath, without versions"() {
        given:
        def project = ProjectBuilder.builder().build()
        def libs = project.file("libs")
        libs.mkdirs()
        def classpath = project.configurations.create("classpath")
        // a project dependency resolves without a repository: one that publishes the coordinates of micronaut-kafka
        def kafka = ProjectBuilder.builder().withName("micronaut-kafka").withParent(project).build()
        kafka.group = "io.micronaut.kafka"
        kafka.configurations.create("default")
        classpath.dependencies.add(project.dependencies.project(path: ":micronaut-kafka"))

        when:
        List<Dependency> dependencies = MicronautDevControlPanel.dependencies(project.dependencies, classpath)

        then:
        dependencies.collect { "${it.group}:${it.name}" as String } == MicronautDevControlPanel.modules(["io.micronaut.kafka:micronaut-kafka"])
        dependencies.every { it.version == null }
    }

    def "the DSL turns Control Panel off"() {
        given:
        def project = ProjectBuilder.builder().build()
        def dev = project.objects.newInstance(MicronautDevExtension)
        dev.controlPanel.enabled.convention(true)

        expect:
        MicronautDevControlPanel.enabled(project, dev).get()

        when:
        dev.controlPanel { it.enabled.set(false) }

        then:
        !MicronautDevControlPanel.enabled(project, dev).get()
    }

    def "the application listens on localhost by default, turned off with the DSL"() {
        given:
        def project = ProjectBuilder.builder().build()
        def dev = project.objects.newInstance(MicronautDevExtension)
        dev.bindLoopback.convention(true)

        expect:
        MicronautDevSupport.bindLoopback(project, dev).get()

        when:
        dev.bindLoopback.set(false)

        then:
        !MicronautDevSupport.bindLoopback(project, dev).get()
    }
}
