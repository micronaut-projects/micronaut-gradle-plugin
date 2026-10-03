package io.micronaut.gradle

import spock.lang.Issue
import spock.lang.Specification

class RunCdsJvmOptionsSpec extends Specification {

    def "splits options variables like the JVM"() {
        expect:
        RunCdsJvmOptions.tokenize(value) == tokens

        where:
        value                                         | tokens
        ''                                            | []
        '  -Xmx1g   -XX:+UseG1GC '                    | ['-Xmx1g', '-XX:+UseG1GC']
        '-javaagent:"/a b/agent.jar" -Dx=\'1 2\''     | ['-javaagent:/a b/agent.jar', '-Dx=1 2']
    }

    def "a launch with its own CDS or AOT cache options manages its archive"() {
        expect:
        options([option]).configuresClassDataSharing() == manages

        where:
        option                              | manages
        '-Xshare:off'                       | true
        '-Xshare:on'                        | true
        '-Xshare:auto'                      | false
        '-XX:SharedArchiveFile=app.jsa'     | true
        '-XX:AOTCache=app.aot'              | true
        '-XX:+AutoCreateSharedArchive'      | true
        '-XX:DumpLoadedClassList=list'      | true
        '-XX:TieredStopAtLevel=1'           | false
        '-Dcom.sun.management.jmxremote'    | false
    }

    def "finds what rules out AOT class linking, in the JVM arguments and in the environment"() {
        expect:
        options(arguments, environment).aotClassLinkingBlocker() == blocker

        where:
        arguments                                                         | environment                                       | blocker
        ['-XX:TieredStopAtLevel=1', '-Dcom.sun.management.jmxremote']     | [:]                                               | null
        ['--add-modules', 'jdk.incubator.vector']                         | [:]                                               | null
        ['-agentlib:jdwp=transport=dt_socket,server=y,suspend=n']         | [:]                                               | 'the launch has a debugger (JDWP)'
        ['-Xrunjdwp:transport=dt_socket']                                 | [:]                                               | 'the launch has a debugger (JDWP)'
        ['-javaagent:/tmp/agent.jar']                                     | [:]                                               | 'the launch has an agent (-javaagent)'
        ['-agentpath:/tmp/libagent.so']                                   | [:]                                               | 'the launch has an agent (-agentpath)'
        []                                                                | [JAVA_TOOL_OPTIONS: '-javaagent:/tmp/agent.jar']  | 'the launch has an agent (-javaagent)'
        []                                                                | [JDK_JAVA_OPTIONS: '-agentlib:jdwp=server=y']     | 'the launch has a debugger (JDWP)'
        ['--add-opens', 'java.base/java.lang=ALL-UNNAMED']                | [:]                                               | 'the launch has --add-opens'
        ['--add-exports=java.base/sun.nio.ch=ALL-UNNAMED']                | [:]                                               | 'the launch has --add-exports'
        ['--patch-module', 'java.base=patch']                             | [:]                                               | 'the launch has --patch-module'
        ['-p', 'mods']                                                    | [:]                                               | 'the launch has --module-path'
        ['--illegal-native-access=warn']                                  | [:]                                               | 'the launch has --illegal-native-access'
        []                                                                | [_JAVA_OPTIONS: '--illegal-native-access=deny']   | 'the launch has --illegal-native-access'
        ['--enable-native-access', 'ALL-UNNAMED']                         | [:]                                               | null
    }

    def "the dump adds the modules that the launch's module graph has, and no system property"() {
        given:
        def launch = RunCdsJvmOptions.of(
                ['-Dcom.sun.management.jmxremote', '-XX:TieredStopAtLevel=1', '--add-modules', 'jdk.incubator.vector', '-Xmx2g', '-Dfoo=bar', '-Xlog:class+load'],
                [JAVA_TOOL_OPTIONS: '-XX:+UseCompactObjectHeaders -javaagent:/tmp/agent.jar'],
                runtimeVersion)

        expect:
        launch.dumpOptions() == ["--add-modules=jdk.incubator.vector,jdk.management.agent$jvmciModule".toString(),
                                 '-XX:+UseCompactObjectHeaders', '-XX:TieredStopAtLevel=1', '-Xmx2g']
        launch.probeOptions() == ['--add-modules=jdk.incubator.vector,jdk.management.agent',
                                  '-XX:+UseCompactObjectHeaders', '-XX:TieredStopAtLevel=1', '-Xmx2g']

        where:
        runtimeVersion           | jvmciModule
        '25.0.4.1'               | ''
        '25.0.3+9-LTS-jvmci-b01' | ',jdk.internal.vm.ci'
    }

    @Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/1388")
    def "the run task's own JVM arguments add no JMX agent module (GraalVM JVM: #graalJvm)"() {
        given:
        def arguments = new MicronautRunJvmArgumentsProvider(graalJvm).asArguments().toList()
        def launch = RunCdsJvmOptions.of(arguments, [:], runtimeVersion)
        def withJmx = RunCdsJvmOptions.of(arguments + '-Dcom.sun.management.jmxremote', [:], runtimeVersion)

        expect:
        launch.dumpModules().toList() == modules
        launch.probeOptions() == arguments
        withJmx.dumpModules().toList() == ['jdk.management.agent'] + modules
        withJmx.probeOptions() == ['--add-modules=jdk.management.agent'] + arguments
        withJmx.keyOptions() != launch.keyOptions()

        where:
        graalJvm | runtimeVersion           | modules
        false    | '25.0.4.1'               | []
        true     | '25.0.3+9-LTS-jvmci-b01' | ['jdk.internal.vm.ci']
    }

    def "the dump leaves out JVMCI flags, and the probe keeps the module options of the launch"() {
        given:
        def launch = options(['-XX:+UnlockExperimentalVMOptions', '-XX:+EnableJVMCI', '--add-opens', 'java.base/java.lang=ALL-UNNAMED',
                              '-XX:StartFlightRecording', '-XX:+HeapDumpOnOutOfMemoryError', '-Xbootclasspath/a:extra.jar'])

        expect:
        launch.dumpModules() == ['jdk.internal.vm.ci'] as Set
        launch.dumpOptions() == ['--add-modules=jdk.internal.vm.ci', '-XX:+UnlockExperimentalVMOptions', '-Xbootclasspath/a:extra.jar']
        launch.probeOptions() == ['-XX:+UnlockExperimentalVMOptions', '-XX:+EnableJVMCI', '--add-opens=java.base/java.lang=ALL-UNNAMED',
                                  '-Xbootclasspath/a:extra.jar']
    }

    def "the dump keeps the enabled native access of the launch, and the probe both native access options"() {
        given:
        def launch = options(['--enable-native-access', 'ALL-UNNAMED', '--illegal-native-access=warn', '-XX:TieredStopAtLevel=1'],
                [JAVA_TOOL_OPTIONS: '--enable-native-access=other.module'])

        expect:
        launch.dumpOptions() == ['--enable-native-access=other.module', '--enable-native-access=ALL-UNNAMED', '-XX:TieredStopAtLevel=1']
        launch.probeOptions() == ['--enable-native-access=other.module', '--enable-native-access=ALL-UNNAMED', '--illegal-native-access=warn',
                                  '-XX:TieredStopAtLevel=1']
    }

    def "the key covers the module and -XX options, but not system properties, agents or logging"() {
        expect:
        options(first).keyOptions() == options(second).keyOptions()

        where:
        first                                                    | second
        ['-XX:TieredStopAtLevel=1', '-Dfoo=bar']                 | ['-XX:TieredStopAtLevel=1', '-Dfoo=baz']
        ['-XX:TieredStopAtLevel=1']                              | ['-XX:TieredStopAtLevel=1', '-Xlog:class+load', '-javaagent:agent.jar']
        ['-Dcom.sun.management.jmxremote']                       | ['-Dcom.sun.management.jmxremote.port=9010']
        ['--add-modules', 'jdk.incubator.vector']                | ['--add-modules=jdk.incubator.vector']
    }

    def "the key changes with the options that the archive depends on"() {
        expect:
        options(first).keyOptions() != options(second).keyOptions()

        where:
        first                                    | second
        ['-XX:TieredStopAtLevel=1']              | []
        ['-Xmx1g']                               | ['-Xmx64g']
        ['-XX:+UseG1GC']                         | ['-XX:+UseZGC']
        ['-Dcom.sun.management.jmxremote']       | []
        []                                       | ['--add-opens=java.base/java.lang=ALL-UNNAMED']
        []                                       | ['--enable-native-access=ALL-UNNAMED']
        []                                       | ['--illegal-native-access=warn']
    }

    def "finds logging of the cds and aot tags"() {
        expect:
        options([option]).logsClassDataSharing() == logs

        where:
        option                          | logs
        '-Xlog:class+load'              | false
        '-Xlog:gc*:file=gc.log'         | false
        '-Xlog:cds'                     | true
        '-Xlog:aot+map=info'            | true
        '-Xlog:all=warning'             | true
        '-Xlog'                         | true
    }

    private static RunCdsJvmOptions options(List<String> arguments, Map<String, String> environment = [:]) {
        RunCdsJvmOptions.of(arguments, environment, '25.0.4.1')
    }
}
