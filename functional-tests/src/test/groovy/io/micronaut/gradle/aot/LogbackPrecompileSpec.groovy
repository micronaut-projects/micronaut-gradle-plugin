package io.micronaut.gradle.aot

import org.gradle.testkit.runner.TaskOutcome

import java.util.jar.JarFile

class LogbackPrecompileSpec extends AbstractAOTPluginSpec {

    private static final String CONFIGURATOR = 'io/micronaut/aot/logback/generated/LogbackConfigurator.class'
    private static final String SERVICE = 'META-INF/services/ch.qos.logback.classic.spi.Configurator'

    def setup() {
        withSample("aot/with-shadow")
        buildFile.text = buildFile.text
                .replace('id("io.micronaut.aot")', 'id("io.micronaut.aot")\n    id("io.micronaut.logback")')
                .replace('runtimeOnly("ch.qos.logback:logback-classic")', 'runtimeOnly("ch.qos.logback:logback-classic:1.5.37")')
        file("src/main/resources/logback.xml").tap { parentFile.mkdirs() }.text = '''<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%msg%n</pattern>
        </encoder>
    </appender>
    <root level="info">
        <appender-ref ref="STDOUT" />
    </root>
</configuration>
'''
    }

    def "precompiles logback.xml into the jar and the fat jar"() {
        when:
        def result = build("jar", "shadowJar")

        then:
        result.task(":precompileLogback").outcome == TaskOutcome.SUCCESS
        def jar = file("build/libs/basic-app-0.1.jar")
        def fatJar = file("build/libs/basic-app-0.1-all.jar")
        hasConfigurator(jar)
        hasConfigurator(fatJar)
        !file("build/classes/java/main/$CONFIGURATOR").exists()
        !file("build/resources/main/$SERVICE").exists()

        when:
        def process = new ProcessBuilder(new File(ShadowMicronautAOTSpec.javaExecutable).canonicalPath,
                '-Dio.micronaut.internal.test.interrupt.startup=true',
                "-jar", fatJar.absolutePath)
                .redirectErrorStream(true)
                .start()
        process.inputStream.text

        then:
        process.waitFor() == 0

        when: "only Java sources change"
        file("src/main/java/demo/app/Extra.java").tap { parentFile.mkdirs() }.text = "package demo.app; class Extra {}"
        result = build("jar")

        then:
        result.task(":precompileLogback").outcome == TaskOutcome.UP_TO_DATE

        when: "logback.xml leaves the literal subset"
        file("src/main/resources/logback.xml").text = file("src/main/resources/logback.xml").text
                .replace('<configuration>', '<configuration>\n    <property name="pattern" value="%msg%n"/>')
        result = build("jar")

        then:
        result.task(":precompileLogback").outcome == TaskOutcome.SUCCESS
        !hasConfigurator(jar)
        file("build/generated/logback/classes").list().length == 0
    }

    def "does not precompile when disabled"() {
        buildFile << """
            micronaut.logback.precompile = false
        """

        when:
        build("jar")

        then:
        !hasConfigurator(file("build/libs/basic-app-0.1.jar"))
    }

    def "keeps the precompiled configurator out of the optimized jar when AOT replaces logback.xml"() {
        buildFile << """
            micronaut.aot.replaceLogbackXml = true
        """

        when:
        build("jar", "optimizedJitJar")

        then:
        hasConfigurator(file("build/libs/basic-app-0.1.jar"))
        def optimized = file("build/libs/basic-app-0.1-jit.jar")
        !hasConfigurator(optimized)
        def services = new JarFile(optimized).withCloseable { jar ->
            jar.getInputStream(jar.getEntry(SERVICE)).text.readLines()*.trim().findAll()
        }
        services.size() == 1
        !services[0].startsWith('io.micronaut.aot.logback.generated')
    }

    def "can be applied without the AOT plugin"() {
        buildFile.text = buildFile.text
                .replace('id("io.micronaut.aot")\n', '')
                .replaceAll(/(?s)\s*aot \{.*?\}/, '')
        buildFile << """
            tasks.register("checkNoAot") {
                def hasAot = micronaut.extensions.findByName("aot") != null
                def hasOptimized = tasks.names.contains("optimizedJitJar")
                doLast {
                    assert !hasAot
                    assert !hasOptimized
                }
            }
        """

        when:
        def result = build("checkNoAot", "jar")

        then:
        result.task(":precompileLogback").outcome == TaskOutcome.SUCCESS
        hasConfigurator(file("build/libs/basic-app-0.1.jar"))
    }

    def "precompiles logback.xml into the JVM Docker image"() {
        buildFile.text = buildFile.text.replace('id("io.micronaut.logback")', 'id("io.micronaut.logback")\n    id("io.micronaut.docker")')

        when:
        build("runnerJar")

        then:
        hasConfigurator(file("build/libs/basic-app-0.1-runner.jar"))
    }

    static boolean hasConfigurator(File jar) {
        new JarFile(jar).withCloseable { it.getEntry(CONFIGURATOR) != null && it.getEntry(SERVICE) != null }
    }
}
