package io.micronaut.gradle

import spock.lang.Issue
import spock.lang.Specification

class MicronautRunJvmArgumentsProviderSpec extends Specification {

    @Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/1388")
    def "run JVM arguments do not start the JMX agent (GraalVM JVM: #graalJvm)"() {
        expect:
        new MicronautRunJvmArgumentsProvider(graalJvm).asArguments().toList() == expected

        where:
        graalJvm | expected
        false    | ['-XX:TieredStopAtLevel=1']
        true     | []
    }
}
