package io.micronaut.gradle.internal

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class MicronautDevOutputLocksSpec extends Specification {

    @TempDir
    Path dir

    def "the lock file of an output is named as the launcher names it, beside the output"() {
        given:
        File output = dir.resolve("build/classes/java/main").toFile()

        expect:
        MicronautDevOutputLocks.lockFile(output) == dir.resolve("build/classes/java/main.micronaut-dev.lock").toAbsolutePath().normalize()
    }

    def "the lock files are taken in the order of their paths, once each"() {
        given:
        def outputs = ["kotlin/main", "java/test", "groovy/main", "java/main", "java/main"].collect { dir.resolve("build/classes/" + it).toFile() }

        expect:
        MicronautDevOutputLocks.lockFiles(outputs)*.toString()*.substring(dir.toAbsolutePath().toString().length() + 1)*.replace(File.separator, "/") == [
            "build/classes/groovy/main.micronaut-dev.lock",
            "build/classes/java/main.micronaut-dev.lock",
            "build/classes/java/test.micronaut-dev.lock",
            "build/classes/kotlin/main.micronaut-dev.lock"
        ]
    }
}
