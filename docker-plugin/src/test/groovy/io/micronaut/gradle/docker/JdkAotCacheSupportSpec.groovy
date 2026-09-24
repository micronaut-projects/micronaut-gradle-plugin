package io.micronaut.gradle.docker

import spock.lang.Specification
import spock.lang.TempDir

import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class JdkAotCacheSupportSpec extends Specification {

    @TempDir
    File dir

    def "pins G1 unless the JVM arguments select a collector"() {
        expect:
        JdkAotCacheSupport.sharedJvmFlags(args) == flags

        where:
        args                                           | flags
        []                                             | ["-XX:+UseG1GC"]
        ["-Xmx256m", "-XX:MaxRAMPercentage=75"]        | ["-XX:+UseG1GC"]
        ["-XX:+UseSerialGC"]                           | []
        ["-Xmx1g", "-XX:+UseParallelGC"]               | []
        ["-XX:+UseZGC"]                                | []
        ["-XX:+UseShenandoahGC"]                       | []
        ["-XX:+UseG1GC"]                               | []
        ["-XX:-UseSerialGC"]                           | ["-XX:+UseG1GC"]
    }

    def "renders the exec form of an instruction"() {
        expect:
        JdkAotCacheSupport.execForm(["bash", "-c", 'echo "a\\b"']) == '["bash", "-c", "echo \\"a\\\\b\\""]'
    }

    def "detects the training run switch in the first JAR that has Micronaut core"() {
        given:
        def withSwitch = jar("with-switch.jar", "io/micronaut/runtime/Micronaut.class", "micronaut.application.training.enabled")
        def withoutSwitch = jar("without-switch.jar", "io/micronaut/runtime/Micronaut.class", "micronaut.application.name")
        def other = jar("other.jar", "example/Other.class", "micronaut.application.training.enabled")
        def notAJar = new File(dir, "resources").tap { mkdirs() }

        expect:
        JdkAotCacheSupport.hasTrainingRunSwitch([notAJar, other, withSwitch, withoutSwitch])
        !JdkAotCacheSupport.hasTrainingRunSwitch([other, withoutSwitch, withSwitch])
        !JdkAotCacheSupport.hasTrainingRunSwitch([other, new File(dir, "missing.jar")])
    }

    private File jar(String name, String entry, String constant) {
        def file = new File(dir, name)
        new JarOutputStream(file.newOutputStream()).withCloseable { out ->
            out.putNextEntry(new JarEntry(entry))
            out.write("io/micronaut/runtime/Micronaut $constant".getBytes("UTF-8"))
            out.closeEntry()
        }
        file
    }
}
