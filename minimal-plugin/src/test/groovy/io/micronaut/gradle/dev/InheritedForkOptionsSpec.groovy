package io.micronaut.gradle.dev

import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class InheritedForkOptionsSpec extends Specification {

    private final Project project = ProjectBuilder.builder().build()
    private final JavaExec run = project.tasks.register("run", JavaExec).get()
    private final JavaExec dev = project.tasks.register("mnDev", JavaExec).get()

    def "the development task takes the JVM arguments, system properties, environment and heap sizes of run"() {
        given:
        run.jvmArgs("-Dfrom.jvm.args=run", "-XX:+UseSerialGC")
        run.systemProperty("micronaut.environments", "h2")
        run.systemProperty("flag", null)
        run.environment("FROM_RUN", "run")
        run.minHeapSize = "64m"
        run.maxHeapSize = "320m"

        when:
        def inherited = InheritedForkOptions.of(run, dev, System::getenv)

        then:
        inherited.jvmArgs() == ["-Dfrom.jvm.args=run", "-XX:+UseSerialGC"]
        inherited.systemProperties() == ["micronaut.environments": "h2", "flag": null]
        // the variables the build has were not added by it
        inherited.environment() == ["FROM_RUN": "run"]
        inherited.minHeapSize() == "64m"
        inherited.maxHeapSize() == "320m"

        when:
        inherited.applyTo(dev)

        then:
        dev.jvmArgs.containsAll(["-Dfrom.jvm.args=run", "-XX:+UseSerialGC"])
        dev.systemProperties["micronaut.environments"] == "h2"
        dev.environment["FROM_RUN"] == "run"
        dev.maxHeapSize == "320m"
    }

    def "what the development task sets itself wins, however either task sets it"() {
        given:
        run.jvmArgs("--enable-native-access=ALL-UNNAMED", "-Draw=run", "-Dboth=run", "-Xmx1g", "-Xms128m")
        run.systemProperty("set.by.property", "run")
        run.systemProperty("set.by.argument", "run")
        run.environment("SHARED", "run")
        run.environment("MODE", "run")
        dev.jvmArgs("--enable-native-access=ALL-UNNAMED", "-Dset.by.argument=mnDev", "-Xms256m")
        dev.systemProperty("raw", "mnDev")
        dev.systemProperty("set.by.property", "mnDev")
        dev.environment("SHARED", "run")
        dev.environment("MODE", "mnDev")
        dev.maxHeapSize = "512m"

        when:
        def inherited = InheritedForkOptions.of(run, dev, System::getenv)

        then:
        inherited.jvmArgs() == ["-Dboth=run"]
        inherited.systemProperties().isEmpty()
        inherited.environment().isEmpty()
        inherited.minHeapSize() == null
        inherited.maxHeapSize() == null
    }

    def "nothing is taken without a task to take it from"() {
        expect:
        !InheritedForkOptions.between(project, "missing", "mnDev").present
    }
}
