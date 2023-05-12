package io.micronaut.gradle.testresources

import spock.lang.Specification

class MicronautTestResourcesPluginTest extends Specification {
    def "ignores appendix when testing versions"() {
        expect:
        MicronautTestResourcesPlugin.parseVersion(version) == expected

        where:
        version        | expected
        '1.0'          | [1, 0]
        '1.1.0'        | [1, 1, 0]
        '1.1-SNAPSHOT' | [1, 1]
        '1.1-M3'       | [1, 1]
    }
}
