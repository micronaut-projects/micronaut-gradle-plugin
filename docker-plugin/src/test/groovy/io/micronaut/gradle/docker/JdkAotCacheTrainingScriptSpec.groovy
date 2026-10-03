package io.micronaut.gradle.docker

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.TempDir

import java.util.concurrent.TimeUnit

import static io.micronaut.gradle.docker.JdkAotCacheSupport.TrainingRunSupport.MODE
import static io.micronaut.gradle.docker.JdkAotCacheSupport.TrainingRunSupport.NONE

/**
 * Runs the training script with the training commands that the plugin generates, and with a stub in place of
 * {@code java} that records how the script starts it.
 */
@Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/1379")
@IgnoreIf({ os.windows || Runtime.version().feature() < 18 })
class JdkAotCacheTrainingScriptSpec extends Specification {

    /**
     * Records its arguments, one line per launch with the arguments separated by tabs, and then does what the
     * script expects of the JVM: the flags that it looks for, a cache, and a successful strict probe. A training
     * run that the script drives answers HTTP requests until SIGTERM stops it, with the web server of the JDK.
     */
    private static final String STUB_JAVA = '''#!/bin/bash
(IFS=$'\\t'; printf '%s\\n' "$*") >> "$STUB_JAVA_LOG"
for arg in "$@"; do
  case $arg in
    -XX:+PrintFlagsFinal)
      echo "    ccstr AOTCacheOutput                           =                                           {product} {default}"
      exit 0 ;;
    -XX:AOTMode=on)
      exit 0 ;;
    -XX:AOTCacheOutput=*)
      printf 'cache' > "${arg#-XX:AOTCacheOutput=}"
      if [[ $STUB_JAVA_PORT != 0 ]]; then
        exec "$STUB_JAVA_HOME/bin/java" -XX:-UsePerfData -m jdk.httpserver -b 127.0.0.1 -p "$STUB_JAVA_PORT" -d "$STUB_JAVA_DIR"
      fi
      exit 0 ;;
  esac
done
exit 1
'''

    @TempDir
    File dir

    def "the #description starts every JVM without performance data"() {
        given:
        def script = new File(dir, JdkAotCacheSupport.TRAINING_SCRIPT)
        script.parentFile.mkdirs()
        script.text = MicronautDockerfile.getResourceAsStream(JdkAotCacheSupport.TRAINING_SCRIPT).text
        def java = new File(dir, "java").tap {
            text = STUB_JAVA
            setExecutable(true)
        }
        def log = new File(dir, "java.log")
        def output = new File(dir, "output.log")
        String cache = new File(dir, JdkAotCacheSupport.CACHE_FILE).absolutePath
        String jar = new File(dir, "application.jar").absolutePath
        int port = support == NONE ? freePort() : 0

        and: "the training command of the plugin, with the stub in place of java"
        def options = ProjectBuilder.builder().withProjectDir(new File(dir, "project")).build().objects.newInstance(JdkAotCacheOptions)
        JdkAotCacheSupport.configureDefaults(options)
        options.trainingMode.set(mode)
        options.trainingPaths.set(paths)
        options.trainingTimeout.set(30)
        options.strictProbes.set(2)
        List<String> command = JdkAotCacheSupport.trainingCommand(dir.absolutePath, ["-Xmx64m"], [port], options, support)
        int javaIndex = command.indexOf("--") + 1
        List<String> jvmCommand = new ArrayList<>(command.subList(javaIndex + 1, command.size()))
        command[javaIndex] = java.absolutePath

        when:
        def builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output)
        builder.environment().putAll([
            STUB_JAVA_LOG : log.absolutePath,
            STUB_JAVA_PORT: String.valueOf(port),
            STUB_JAVA_HOME: System.getProperty("java.home"),
            STUB_JAVA_DIR : dir.absolutePath
        ])
        def process = builder.start()
        if (!process.waitFor(2, TimeUnit.MINUTES)) {
            process.destroyForcibly().waitFor()
        }

        then:
        process.exitValue() == 0
        output.text.contains("[jdk-aot-cache] Wrote $cache")
        output.text.contains("[jdk-aot-cache] 2 strict probes passed")
        jvmCommand.subList(0, expected.size()) == expected
        support != NONE || output.text.contains("[jdk-aot-cache] GET /: 200")

        and: "the flags check, the training run and the strict probes"
        def probe = ["-XX:-UsePerfData", "-XX:AOTMode=on", "-XX:AOTCache=$cache".toString()] + jvmCommand.takeWhile { it != "-jar" } + ["-cp", jar, "-version"]
        log.readLines()*.split("\t")*.toList() == [
            ["-XX:-UsePerfData", "-XX:+UnlockDiagnosticVMOptions", "-XX:+PrintFlagsFinal", "-version"],
            ["-XX:-UsePerfData", "-XX:AOTCacheOutput=$cache".toString()] + jvmCommand,
            probe,
            probe
        ]

        where:
        description                           | mode    | paths  | support | expected
        "load training run"                   | null    | []     | MODE    | ["-Dmicronaut.application.training.enabled=true", "-Dmicronaut.application.training.mode=load"]
        "training run switch in start mode"   | "start" | ["/a"] | MODE    | ["-Dmicronaut.application.training.enabled=true", "-Dmicronaut.application.training.mode=start", "-Dmicronaut.application.training.warmup.paths=/a"]
        "training run that the script drives" | "start" | ["/"]  | NONE    | ["-XX:+UseG1GC", "-Xmx64m", "-jar"]
    }

    private static int freePort() {
        new ServerSocket(0).withCloseable { it.localPort }
    }
}
