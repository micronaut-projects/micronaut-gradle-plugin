package io.micronaut.gradle.aot

import groovy.transform.CompileStatic
import io.micronaut.gradle.AbstractGradleBuildSpec

@CompileStatic
abstract class AbstractAOTPluginSpec extends AbstractGradleBuildSpec {
    def setup() {
        withSample('aot/app-startup-fixture')
    }

    void hasAOTConfiguration(String name, @DelegatesTo(value = AOTConfiguration, strategy = Closure.DELEGATE_FIRST) Closure<?> spec = {}) {
        def config = new AOTConfiguration(file("build/generated/aot/${name}.properties"))
        config.exists()
        spec.delegate = config
        spec.resolveStrategy = Closure.DELEGATE_FIRST
        spec()
    }

    void interruptApplicationStartup() {
        buildFile << """
            tasks.withType(JavaExec).configureEach {
                systemProperties.put('io.micronaut.internal.test.interrupt.startup', 'true')
            }
        """
    }

    static class AOTConfiguration {
        private final File configFile
        private final Properties props

        AOTConfiguration(File configFile) {
            this.configFile = configFile
            this.props = new Properties()
            if (configFile.exists()) {
                configFile.newInputStream().withCloseable {
                    props.load(it)
                }
            }
        }

        void exists() {
            assert configFile.exists() : "Expected to find effective configuration file at $configFile but it doesn't exist"
        }

        void withPropertyKeys(String... keys) {
            Set<String> actualKeys = props.keySet().collect { it.toString() } as Set<String>
            Set<String> expectedKeys = keys as Set<String>
            assert actualKeys.containsAll(expectedKeys)
        }

        void withProperty(String key, String expectedValue) {
            assert props.containsKey(key)
            String actualValue = "${props.get(key)}"
            assert actualValue == expectedValue
        }

        void withProperty(String key) {
            assert props.containsKey(key)
        }
    }
}
