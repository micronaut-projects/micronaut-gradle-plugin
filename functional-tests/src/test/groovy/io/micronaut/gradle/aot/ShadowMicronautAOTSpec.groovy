package io.micronaut.gradle.aot

import spock.lang.Requires

@Requires({ jvm.isJava11Compatible() })
class ShadowMicronautAOTSpec extends AbstractAOTPluginSpec {

    def "builds a fatjar optimized flavor"() {
        Process process
        withSample("aot/with-shadow")

        when:
        def result = build("optimizedJitJarAll")

        then:
        def fatJar = file("build/libs/basic-app-0.1-all-optimized.jar")
        fatJar.exists()

        when:
        def builder = new ProcessBuilder(new File(javaExecutable).canonicalPath,
                '-Dio.micronaut.internal.test.interrupt.startup=true',
                "-jar", fatJar.absolutePath)
        def runnerOutput = file("runner.txt")
        builder.redirectError(runnerOutput)
        builder.redirectOutput(runnerOutput)

        process = builder.start()
        Thread.startDaemon("process monitor") {
            // Sanity interruption if process doesn't stop as expected
            Thread.sleep(10000)
            process?.destroy()
        }
        def exitCode = process.waitFor()

        then:
        def outputText = runnerOutput.text
        println(outputText)
        exitCode == 0
        [
                'io.micronaut.core.reflect.ClassUtils$Optimizations',
                'io.micronaut.core.util.EnvironmentProperties',
                'io.micronaut.core.async.publisher.PublishersOptimizations',
                'io.micronaut.core.io.service.SoftServiceLoader$Optimizations',
                'io.micronaut.context.env.ConstantPropertySources'
        ].each {
            assert outputText.contains("Setting optimizations for class $it")
        }

    }

    static String getJavaExecutable() {
        String exec = System.getProperty("java.home") + "/bin/java".replace((char) '/', (char) File.separatorChar)
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            exec += ".exe"
        }
        exec
    }

}
