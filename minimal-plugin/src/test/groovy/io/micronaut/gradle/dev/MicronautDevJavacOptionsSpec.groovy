package io.micronaut.gradle.dev

import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class MicronautDevJavacOptionsSpec extends Specification {

    void "the JVM arguments of a forked compile reach the embedded compiler as javac's own -J options"() {
        given:
        JavaCompile compile = javaCompile()
        compile.options.fork = true
        compile.options.forkOptions.jvmArgs = ["-Dmicronaut.data.sql.dialect-options.oracle.version=23.1", "-Xmx1g"]

        when:
        List<String> options = MicronautDevSupport.javacOptions(compile)

        then:
        options.containsAll(["-J-Dmicronaut.data.sql.dialect-options.oracle.version=23.1", "-J-Xmx1g"])
    }

    void "the JVM arguments of a compile that does not fork are not used by the build, nor by the embedded compiler"() {
        given:
        JavaCompile compile = javaCompile()
        compile.options.fork = false
        compile.options.forkOptions.jvmArgs = ["-Dignored=true"]

        expect:
        !MicronautDevSupport.javacOptions(compile).any { it.startsWith("-J") }
    }

    private static JavaCompile javaCompile() {
        Project project = ProjectBuilder.builder().build()
        project.pluginManager.apply("java")
        return (JavaCompile) project.tasks.getByName("compileJava")
    }
}
