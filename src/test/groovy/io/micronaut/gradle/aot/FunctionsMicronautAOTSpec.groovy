package io.micronaut.gradle.aot

import io.micronaut.gradle.AbstractGradleBuildSpec
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Requires

class FunctionsMicronautAOTSpec extends AbstractAOTPluginSpec {
    private static final List<String> PROVIDERS = ["aws"]
    private static final List<String> RUNTIMES = ["jit", "native"]

    def "can optimize a #provider function on #runtime"() {
        withSample("aot/${provider}-function")

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
            withProperty('deduce.environment.enabled', 'false')
            withExtraPropertyKeys 'service.types', 'known.missing.types.list'
        }

        then: "prepares optimizations"
        result.output.contains 'Converting application.yml into Java based configuration'

        when:
        result = build "optimizedJitJar", "optimizedJitJarAll"

        then: "builds optimized jars"
        result.task(":optimizedJitJar").outcome == TaskOutcome.SUCCESS
        result.task(":optimizedJitJarAll").outcome == TaskOutcome.SUCCESS

        where:
        [provider,runtime] << [PROVIDERS, RUNTIMES].combinations()

    }

    @Requires({ AbstractGradleBuildSpec.graalVmAvailable && !os.windows })
    def "can build a native library function for #provider"() {
        withSample("aot/${provider}-function")

        when:
        def result = build "nativeOptimizedCompile"

        then:
        result.task(":nativeOptimizedCompile").outcome == TaskOutcome.SUCCESS

        where:
        provider << PROVIDERS
    }
}
