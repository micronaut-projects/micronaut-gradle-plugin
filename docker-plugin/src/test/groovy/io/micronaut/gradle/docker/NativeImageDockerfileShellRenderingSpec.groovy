package io.micronaut.gradle.docker

import spock.lang.Specification

class NativeImageDockerfileShellRenderingSpec extends Specification {

    void "renderShellCommand quotes shell metacharacters"() {
        given:
        def commandLine = [
                'native-image',
                '-cp',
                '/home/app/libs/*.jar:/home/app/resources:/home/app/application.jar',
                '--initialize-at-build-time=io.micronaut.flyway.StaticResourceProvider$StaticLoadableResource',
                '-H:IncludeResources=application(-|.)(foo|bar)?[.]properties',
                "contains'apostrophe"
        ]

        expect:
        renderShellCommand(commandLine) == "native-image -cp '/home/app/libs/*.jar:/home/app/resources:/home/app/application.jar' '--initialize-at-build-time=io.micronaut.flyway.StaticResourceProvider\$StaticLoadableResource' '-H:IncludeResources=application(-|.)(foo|bar)?[.]properties' 'contains'\"'\"'apostrophe'"
    }

    private static String renderShellCommand(List<String> commandLine) {
        def method = NativeImageDockerfile.getDeclaredMethod('renderShellCommand', List)
        method.accessible = true
        return (String) method.invoke(null, commandLine)
    }
}
