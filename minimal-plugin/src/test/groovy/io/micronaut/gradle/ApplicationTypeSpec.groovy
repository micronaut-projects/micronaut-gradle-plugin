package io.micronaut.gradle

import spock.lang.Specification

class ApplicationTypeSpec extends Specification {

    void "ApplicationType:toString() returns name"(ApplicationType applicationType,
                                                   String expected) {
        expect:
        expected == applicationType.toString()

        where:
        applicationType             || expected
        ApplicationType.DEFAULT     || "DEFAULT"
        ApplicationType.FUNCTION    || "FUNCTION"
        ApplicationType.CLI         || "CLI"
        ApplicationType.GRPC        || "GRPC"
        ApplicationType.MESSAGING   || "MESSAGING"
    }
}
