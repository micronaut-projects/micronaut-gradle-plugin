package io.micronaut.gradle.aot

class BasicMicronautAOTSpec extends AbstractAOTPluginSpec {

    def "generates optimizations for #runtime (#kind)"() {
        withSample("aot/basic-app")
        withPlugins(kind)

        when:
        def result = build "prepare${runtime.capitalize()}Optimizations", "-i"

        and: "configuration file is generated"
        hasAOTConfiguration(runtime) {
            withProperty('graalvm.config.enabled', 'native' == runtime ? 'true' : 'false')
            withProperty('logback.xml.to.java.enabled', 'false')
            withProperty('cached.environment.enabled', 'true')
            withProperty('serviceloading.jit.enabled', 'true')
            withProperty('serviceloading.native.enabled', 'true')
            withProperty('yaml.to.java.config.enabled', 'true')
            withProperty('scan.reactive.types.enabled', 'true')
            withProperty('known.missing.types.enabled', 'true')
            withProperty('sealed.property.source.enabled', 'true')
            withProperty('precompute.environment.properties.enabled', 'true')
            withProperty('deduce.environment.enabled', 'true')
            withExtraPropertyKeys 'service.types', 'known.missing.types.list'
        }

        then: "Context configurer is loaded"
        result.output.contains 'Java configurer loaded'

        when:
        interruptApplicationStartup()
        result = build("optimizedRun")

        then:
        [
                'io.micronaut.core.reflect.ClassUtils$Optimizations',
                'io.micronaut.core.util.EnvironmentProperties',
                'io.micronaut.core.async.publisher.PublishersOptimizations',
                'io.micronaut.core.io.service.SoftServiceLoader$Optimizations',
                'io.micronaut.context.env.ConstantPropertySources'
        ].each {
            assert result.output.contains("Setting optimizations for class $it")
        }

        where:
        runtime  | kind
        'jit'    | Plugins.MINIMAL_APPLICATION
        'jit'    | Plugins.APPLICATION
        'native' | Plugins.MINIMAL_APPLICATION
        'native' | Plugins.APPLICATION

    }

    def "can configure properties via the DSL #runtime (#kind)"() {
        withSample("aot/basic-app")
        withPlugins(kind)
        buildFile << """
            micronaut {
                aot {
                    configurationProperties.putAll([
                        'known.missing.types.enabled': 'false',
                        'some.plugin.enabled': 'true'
                    ])
                }
            }

        """

        when:
        def result = build "prepare${runtime.capitalize()}Optimizations", "-i"

        and: "configuration file is generated"
        hasAOTConfiguration(runtime) {
            withProperty('graalvm.config.enabled', 'native' == runtime ? 'true' : 'false')
            withProperty('logback.xml.to.java.enabled', 'false')
            withProperty('cached.environment.enabled', 'true')
            withProperty('serviceloading.jit.enabled', 'true')
            withProperty('serviceloading.native.enabled', 'true')
            withProperty('yaml.to.java.config.enabled', 'true')
            withProperty('scan.reactive.types.enabled', 'true')
            withProperty('known.missing.types.enabled', 'false')
            withProperty('sealed.property.source.enabled', 'true')
            withProperty('precompute.environment.properties.enabled', 'true')
            withProperty('deduce.environment.enabled', 'true')
            withProperty('some.plugin.enabled', 'true')
            withExtraPropertyKeys 'service.types', 'known.missing.types.list'
        }

        then: "Context configurer is loaded"
        result.output.contains 'Java configurer loaded'

        when:
        interruptApplicationStartup()
        result = build("optimizedRun")

        then:
        [
                'io.micronaut.core.util.EnvironmentProperties',
                'io.micronaut.core.async.publisher.PublishersOptimizations',
                'io.micronaut.core.io.service.SoftServiceLoader$Optimizations',
                'io.micronaut.context.env.ConstantPropertySources'
        ].each {
            assert result.output.contains("Setting optimizations for class $it")
        }

        where:
        runtime  | kind
        'jit'    | Plugins.MINIMAL_APPLICATION
        'jit'    | Plugins.APPLICATION
        'native' | Plugins.MINIMAL_APPLICATION
        'native' | Plugins.APPLICATION

    }

    def "can optimize an application using the minimal application plugin"() {
        withSample("aot/basic-app")
        withPlugins(Plugins.MINIMAL_APPLICATION)

        when:
        interruptApplicationStartup()
        def result = build("optimizedRun")

        then:
        [
                'io.micronaut.core.reflect.ClassUtils$Optimizations',
                'io.micronaut.core.util.EnvironmentProperties',
                'io.micronaut.core.async.publisher.PublishersOptimizations',
                'io.micronaut.core.io.service.SoftServiceLoader$Optimizations',
                'io.micronaut.context.env.ConstantPropertySources'
        ].each {
            assert result.output.contains("Setting optimizations for class $it")
        }

    }

}
