package io.micronaut.gradle.aot

import io.micronaut.gradle.AbstractGradleBuildSpec
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.Requires

class BasicMicronautAOTSpec extends AbstractAOTPluginSpec {

    def "generates optimizations for #runtime (#kind)"() {
        withSample("aot/basic-app")
        withPlugins(kind)

        when:
        def result = build "prepare${runtime.capitalize()}Optimizations", "-i"

        and: "configuration file is generated"
        hasAOTConfiguration(runtime) {
            withProperty('graalvm.config.enabled', 'native' == runtime ? 'true' : 'false')
            withProperty('logback.xml.to.java.enabled', 'true')
            withProperty('cached.environment.enabled', 'true')
            withProperty('yaml.to.java.config.enabled', 'true')
            withProperty('property-source-loader.generate.enabled', 'true')
            withProperty('property-source-loader.types', 'io.micronaut.context.env.yaml.YamlPropertySourceLoader')
            withProperty('scan.reactive.types.enabled', 'true')
            withProperty('known.missing.types.enabled', 'true')
            withProperty('sealed.property.source.enabled', 'true')
            withProperty('precompute.environment.properties.enabled', 'true')
            withProperty('deduce.environment.enabled', 'true')
            withProperty('netty.properties.enabled', 'true')
            withExtraPropertyKeys 'known.missing.types.list'
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
            withProperty('logback.xml.to.java.enabled', 'true')
            withProperty('cached.environment.enabled', 'true')
            withProperty('yaml.to.java.config.enabled', 'true')
            withProperty('property-source-loader.generate.enabled', 'true')
            withProperty('property-source-loader.types', 'io.micronaut.context.env.yaml.YamlPropertySourceLoader')
            withProperty('scan.reactive.types.enabled', 'true')
            withProperty('known.missing.types.enabled', 'false')
            withProperty('sealed.property.source.enabled', 'true')
            withProperty('precompute.environment.properties.enabled', 'true')
            withProperty('deduce.environment.enabled', 'true')
            withProperty('some.plugin.enabled', 'true')
            withProperty('netty.properties.enabled', 'true')
            withExtraPropertyKeys 'known.missing.types.list'
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
                'io.micronaut.context.env.ConstantPropertySources'
        ].each {
            assert result.output.contains("Setting optimizations for class $it")
        }

    }

    @Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/401")
    def "supports spaces in file names"() {
        withSpacesInTestDir()
        withSample("aot/basic-app")
        withPlugins(Plugins.MINIMAL_APPLICATION)
        println("Base directory: $baseDir")

        when:
        def result = build "prepareJitOptimizations"

        then:
        result.task(":prepareJitOptimizations").outcome == TaskOutcome.SUCCESS
    }

    @Requires({ AbstractGradleBuildSpec.graalVmAvailable && !os.windows })
    def "can compile standard and optimized native apps"() {
        withSample("aot/basic-app")
        withPlugins(Plugins.APPLICATION)

        when:
        def result = build(task)

        then:
        result.task(":$task").outcome == TaskOutcome.SUCCESS

        where:
        task << ["nativeCompile", "nativeOptimizedCompile"]

    }

    @Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/803")
    def "supports main class not in package"() {
        withSample("aot/basic-app")
        withPlugins(kind)
        buildFile << """
            application {
                mainClass.set("NioHttpServer")
            }
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
            withProperty('logback.xml.to.java.enabled', 'true')
            withProperty('cached.environment.enabled', 'true')
            withProperty('yaml.to.java.config.enabled', 'true')
            withProperty('property-source-loader.generate.enabled', 'true')
            withProperty('property-source-loader.types', 'io.micronaut.context.env.yaml.YamlPropertySourceLoader')
            withProperty('scan.reactive.types.enabled', 'true')
            withProperty('known.missing.types.enabled', 'false')
            withProperty('sealed.property.source.enabled', 'true')
            withProperty('precompute.environment.properties.enabled', 'true')
            withProperty('deduce.environment.enabled', 'true')
            withProperty('some.plugin.enabled', 'true')
            withProperty('netty.properties.enabled', 'true')
            withExtraPropertyKeys 'known.missing.types.list'
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
                'io.micronaut.context.env.ConstantPropertySources'
        ].each {


            def outputDir = new File("$baseDir/build/generated/aot/jit/sources/")
            def expectedPackage = "io.micronaut.aot.generated"
            def expectectedTargetDir = expectedPackage.replace(".", "/")
            assert calculatePossiblePackages(outputDir).contains(expectectedTargetDir)

        }

        where:
        runtime  | kind
        'jit'    | Plugins.MINIMAL_APPLICATION
        'jit'    | Plugins.APPLICATION
        'native' | Plugins.MINIMAL_APPLICATION
        'native' | Plugins.APPLICATION


    }

    @Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/888")
    def "prepare optimizations task is cacheable"() {
        withSample("aot/basic-app")
        withPlugins(Plugins.MINIMAL_APPLICATION)
        file("gradle.properties") << "org.gradle.caching=true"
        // Add a random seed to the build.gradle file so that the task is not from cache
        // when a previous test execution has already cached the result
        file("build.gradle") << """
            tasks.named("prepareJitOptimizations") {
                inputs.property("seed", "${System.currentTimeMillis()}")
            }
        """

        when:
        def result = build "prepareJitOptimizations"

        then:
        result.task(":prepareJitOptimizations").outcome == TaskOutcome.SUCCESS

        when:
        result = build "clean", "prepareJitOptimizations"

        then:
        result.task(":prepareJitOptimizations").outcome == TaskOutcome.FROM_CACHE
    }

    private List<GString> calculatePossiblePackages(File outputDir) {
        def list = new ArrayList()
        outputDir.eachDirRecurse { list.add(subpath(it, outputDir)) }
        return list
    }

    private String subpath(File fullpath, File basePath) {
        def path = fullpath.getAbsolutePath()
        path.substring(basePath.getAbsolutePath().length() + 1, path.size()).replaceAll("\\\\", "/")
    }

}
