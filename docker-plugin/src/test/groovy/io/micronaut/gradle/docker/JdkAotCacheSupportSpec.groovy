package io.micronaut.gradle.docker

import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

import static io.micronaut.gradle.docker.JdkAotCacheSupport.TrainingRunSupport.MODE
import static io.micronaut.gradle.docker.JdkAotCacheSupport.TrainingRunSupport.NONE
import static io.micronaut.gradle.docker.JdkAotCacheSupport.TrainingRunSupport.SWITCH
import static io.micronaut.gradle.docker.JdkAotCacheTrainingMode.LOAD
import static io.micronaut.gradle.docker.JdkAotCacheTrainingMode.START

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

    def "detects what the first JAR that has Micronaut core offers for a training run"() {
        given:
        def withMode = jar("with-mode.jar", "micronaut.application.training.enabled", "micronaut.application.training.mode")
        def withSwitch = jar("with-switch.jar", "micronaut.application.training.enabled", "micronaut.application.name")
        def withoutSwitch = jar("without-switch.jar", "micronaut.application.name", "micronaut.application.name")
        def modeWithoutSwitch = jar("mode-without-switch.jar", "micronaut.application.name", "micronaut.application.training.mode")
        def other = jar("other.jar", ["example/Other.class": "micronaut.application.training.enabled micronaut.application.training.mode"])
        def withoutConfiguration = jar("without-configuration.jar", ["io/micronaut/runtime/Micronaut.class": "micronaut.application.training.enabled micronaut.application.training.mode"])
        def notAJar = new File(dir, "resources").tap { mkdirs() }
        def unreadable = new File(dir, "unreadable.jar").tap { text = "not a ZIP file" }

        expect:
        JdkAotCacheSupport.trainingRunSupport(classpath(notAJar, unreadable, other, withMode, withoutSwitch)) == MODE
        JdkAotCacheSupport.trainingRunSupport(classpath(other, withSwitch, withMode)) == SWITCH
        JdkAotCacheSupport.trainingRunSupport(classpath(other, withoutSwitch, withMode)) == NONE
        JdkAotCacheSupport.trainingRunSupport(classpath(other, new File(dir, "missing.jar"))) == NONE

        and: "the mode is a property that ApplicationConfiguration declares, in a core that has the switch"
        JdkAotCacheSupport.trainingRunSupport(classpath(withoutConfiguration, withMode)) == SWITCH
        JdkAotCacheSupport.trainingRunSupport(classpath(modeWithoutSwitch, withMode)) == NONE
    }

    def "on a Micronaut core that has the modes, the training run is in the #mode mode when the build asks for #requested"() {
        expect:
        JdkAotCacheSupport.trainingCommand("/home/app", [], [8080], options(requested), MODE)
            .contains("-Dmicronaut.application.training.mode=$mode" as String)

        where:
        requested | mode
        null      | "load"
        LOAD      | "load"
        START     | "start"
    }

    def "the #requested mode fails when Micronaut core offers #support"() {
        when:
        JdkAotCacheSupport.trainingCommand("/home/app", [], [8080], options(requested), support)

        then:
        def e = thrown(GradleException)
        e.message == "The JDK AOT cache training mode is LOAD, but the application's Micronaut version has no such mode: " +
            "it needs a Micronaut Core with the micronaut.application.training.mode property. " +
            "Upgrade Micronaut, or remove trainingMode to train with a run that starts the application"

        where:
        requested | support
        LOAD      | SWITCH
        LOAD      | NONE
    }

    def "training paths fail when the training mode is #description"() {
        when:
        JdkAotCacheSupport.validate(options(requested, ["/hello"]))

        then:
        def e = thrown(GradleException)
        e.message == "The JDK AOT cache training paths are only requested by a training run that starts the application, " +
            "but the training mode is $description: set trainingMode to START, or remove the training paths"

        where:
        requested | description
        null      | "not set"
        LOAD      | "LOAD"
    }

    def "training paths are valid in the start mode, and no path is valid in every mode"() {
        when:
        JdkAotCacheSupport.validate(options(requested, paths))

        then:
        noExceptionThrown()

        where:
        requested | paths
        START     | ["/hello", "/books?page=1"]
        START     | []
        LOAD      | []
        null      | []
    }

    def "the training command for #requested on a core that offers #support is #expected"() {
        given:
        def options = options(requested, paths)

        expect:
        JdkAotCacheSupport.trainingCommand("/home/app", ["-Xmx64m"], ports, options, support) ==
            ["bash", "/home/app/jdk-aot-cache/train.sh", "--cache", "/home/app/application.aot", "--timeout", "120", "--compatible-oop-compression"] +
            expected + ["-XX:+UseG1GC", "-Xmx64m", "-jar", "/home/app/application.jar"]

        where:
        requested | paths             | ports  | support | expected
        null      | []                | []     | MODE    | ["--training-run", "--", "java", "-Dmicronaut.application.training.enabled=true", "-Dmicronaut.application.training.mode=load"]
        LOAD      | []                | [8080] | MODE    | ["--training-run", "--", "java", "-Dmicronaut.application.training.enabled=true", "-Dmicronaut.application.training.mode=load"]
        START     | []                | []     | MODE    | ["--training-run", "--", "java", "-Dmicronaut.application.training.enabled=true", "-Dmicronaut.application.training.mode=start"]
        START     | ["/a", "/b?c=d"]  | [8080] | MODE    | ["--training-run", "--", "java", "-Dmicronaut.application.training.enabled=true", "-Dmicronaut.application.training.mode=start", "-Dmicronaut.application.training.warmup.paths=/a,/b?c=d"]
        null      | []                | []     | SWITCH  | ["--training-run", "--", "java", "-Dmicronaut.application.training.enabled=true"]
        START     | ["/a"]            | [8080] | SWITCH  | ["--training-run", "--", "java", "-Dmicronaut.application.training.enabled=true", "-Dmicronaut.application.training.warmup.paths=/a"]
        null      | []                | [9090] | NONE    | ["--port", "9090", "--", "java"]
        START     | ["/a", "/b?c=d"]  | [9090] | NONE    | ["--port", "9090", "--path", "/a", "--path", "/b?c=d", "--", "java"]
    }

    def "a training run that the script drives needs an exposed port"() {
        when:
        JdkAotCacheSupport.trainingCommand("/home/app", [], [], options(null), NONE)

        then:
        def e = thrown(GradleException)
        e.message.contains("the image exposes no port")
    }

    def "only a run that starts the application although the build did not ask for it is logged at lifecycle level"() {
        given:
        def logger = Mock(Logger)

        when:
        JdkAotCacheSupport.logTrainingMode(logger, options(requested), support)

        then:
        lifecycle * logger.lifecycle("JDK AOT cache: the application's Micronaut version has no {} training mode ({}), so the training run starts the application while the image is built, " +
            "where the services it needs to start must be available. " +
            "Use a Micronaut version that has the {} mode to train without starting the application, or set trainingMode to {} to confirm this run",
            LOAD, "micronaut.application.training.mode", LOAD, START)
        (1 - lifecycle) * logger.info(*_)
        0 * logger._

        where:
        requested | support | lifecycle
        null      | MODE    | 0
        LOAD      | MODE    | 0
        START     | MODE    | 0
        null      | SWITCH  | 1
        START     | SWITCH  | 0
        null      | NONE    | 1
        START     | NONE    | 0
    }

    def "the training run #message when the build asks for #requested and Micronaut core offers #support"() {
        given:
        def logger = Mock(Logger)

        when:
        JdkAotCacheSupport.logTrainingMode(logger, options(requested), support)

        then:
        1 * logger.info({ it.contains(message) }, *_)
        0 * logger._

        where:
        requested | support | message
        null      | MODE    | "loads the bean definitions of the application and does not start it"
        LOAD      | MODE    | "loads the bean definitions of the application and does not start it"
        START     | MODE    | "starts the application in the image build"
        START     | SWITCH  | "starts the application in the image build"
        START     | NONE    | "starts the application in the image build"
    }

    private JdkAotCacheOptions options(JdkAotCacheTrainingMode trainingMode, List<String> trainingPaths = []) {
        def options = ProjectBuilder.builder().withProjectDir(dir).build().objects.newInstance(JdkAotCacheOptions)
        JdkAotCacheSupport.configureDefaults(options)
        options.trainingMode.set(trainingMode)
        options.trainingPaths.set(trainingPaths)
        options
    }

    private static List<File> classpath(File... files) {
        files as List
    }

    /**
     * A JAR that looks like Micronaut core to the plugin, which only looks for the properties in the class bytes.
     */
    private File jar(String name, String micronautConstant, String applicationConfigurationConstant) {
        jar(name, [
            "io/micronaut/runtime/Micronaut.class"               : micronautConstant,
            "io/micronaut/runtime/ApplicationConfiguration.class": applicationConfigurationConstant
        ])
    }

    private File jar(String name, Map<String, String> entries) {
        def file = new File(dir, name)
        new JarOutputStream(file.newOutputStream()).withCloseable { out ->
            entries.each { entry, constants ->
                out.putNextEntry(new JarEntry(entry))
                out.write("io/micronaut/runtime/Micronaut $constants".getBytes("UTF-8"))
                out.closeEntry()
            }
        }
        file
    }
}
