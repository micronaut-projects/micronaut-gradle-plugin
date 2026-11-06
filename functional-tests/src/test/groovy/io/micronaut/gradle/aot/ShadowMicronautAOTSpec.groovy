package io.micronaut.gradle.aot

import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue

import java.util.jar.JarFile

class ShadowMicronautAOTSpec extends AbstractAOTPluginSpec {

    def "builds a fatjar optimized flavor"() {
        Process process
        withSample("aot/with-shadow")

        when:
        def result = build("optimizedJitJarAll")

        then:
        def fatJar = file("build/libs/basic-app-0.1-all-optimized.jar")
        fatJar.exists()
        result.task(":transformOptimizedJitClassPath") == null
        !hasDesugaredLambdas(fatJar)

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
                'io.micronaut.context.env.ConstantPropertySources'
        ].each {
            assert outputText.contains("Setting optimizations for class $it")
        }

    }

    @Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/1393")
    def "rewrites the class path of the fat jars"() {
        withSample("aot/with-shadow")
        buildFile << """
            micronaut.classPathTransform {
                desugarLambdas = true
                stripLocalVariables = true
            }
        """

        when:
        def result = build("shadowJar", "optimizedJitJarAll")

        then:
        result.task(":transformClassPath").outcome == TaskOutcome.SUCCESS
        result.task(":transformOptimizedJitClassPath").outcome == TaskOutcome.SUCCESS
        result.output.contains("Desugared ")
        result.output.contains("Stripped local-variable tables")
        def fatJar = file("build/libs/basic-app-0.1-all.jar")
        def optimizedFatJar = file("build/libs/basic-app-0.1-all-optimized.jar")
        hasDesugaredLambdas(fatJar)
        hasDesugaredLambdas(optimizedFatJar)

        when:
        def process = new ProcessBuilder(new File(javaExecutable).canonicalPath,
                '-Dio.micronaut.internal.test.interrupt.startup=true',
                "-jar", optimizedFatJar.absolutePath)
                .redirectErrorStream(true)
                .start()
        def output = process.inputStream.text

        then:
        process.waitFor() == 0
        output.contains("Setting optimizations for class io.micronaut.core.util.EnvironmentProperties")
    }

    static boolean hasDesugaredLambdas(File jar) {
        new JarFile(jar).withCloseable { it.entries().toList().any { it.name.contains('$$Lambda$R') } }
    }

    static String getJavaExecutable() {
        String exec = System.getProperty("java.home") + "/bin/java".replace((char) '/', (char) File.separatorChar)
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            exec += ".exe"
        }
        exec
    }

}
