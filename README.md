# Micronaut Gradle Plugin

A Gradle Plugin for Micronaut that configures the Micronaut BOM and sets up annotation processing.

This project consists of 2 plugins: one for applications and one for Micronaut libraries.

## Micronaut Library Plugin

```groovy
plugins {
  id "io.micronaut.library" version "{version}"
}
```

The [Micronaut library plugin](https://plugins.gradle.org/plugin/io.micronaut.library) sets up basic annotation processing and applies the `java-library` plugin. The `micronaut` DSL can be used to configure how this behaves.

The minimum requirement is to set the Micronaut version to use. This can be done by setting `micronautVersion` in `gradle.properties` or as follows in `build.gradle`:

```groovy
micronaut {
    version "2.0.0.RC1"
}
```

Complete example with the default settings:

```groovy

micronaut {
    version "2.0.0.RC1"
    processing {
        // Sets whether incremental annotation processing is enabled
        incremental true
        // Sets the module name.
        // This should be the same as the artifactId in the POM
        module project.name
        // Sets the group.
        // This should be th same as the groupId in the POM
        group project.group
        // Sets the Java package names containing any custom Micronaut 
        // meta annotations (new annotations annotated with say @Around). 
        // Generally used only for advanced cases such as defining new AOP
        // advice. If omitted however, incremental annotation processing
        // will not work correctly
        anntations "com.example.*"
    }   
}
```

Note that the Micronaut Library plugin can also be used with Groovy and Kotlin.

### Kotlin Support

For Kotlin, the Kotlin `jvm` and `kapt` plugins must be configured:

```groovy
plugins {
    id "org.jetbrains.kotlin.jvm" version "{kotlin.version}"
    id "org.jetbrains.kotlin.kapt" version "{kotlin.version}"
    id "io.micronaut.library" version "{version}"   
}
```

### Minimal Build


### Minimal Build

With the `io.micronaut.library` plugin applied a minimal build to get started writing a library for Micronaut that written in Java and is tested with JUnit 5 looks like:

```groovy
plugins {
    id 'io.micronaut.library' version '1.0.0.M3'
}

version "0.1"
group "com.example"

repositories {
    mavenCentral()
}

micronaut {
    version = "2.0.0.RC1"
}

dependencies {
    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

test {
    useJUnitPlatform()
}
```

## Micronaut Application Plugin

```groovy
plugins {
  id "io.micronaut.application" version "{version}"
}
```

The [Micronaut application plugin](https://plugins.gradle.org/plugin/io.micronaut.application) extends the Micronaut Library plugin and instead applies the Gradle `application` plugin as well.

In addition, the plugin correctly configures the `run` task so it can be used with continuous build:

```bash
$ ./gradlew run --t
```

### Minimal Build

With the `io.micronaut.application` plugin applied a minimal build to get started with a Micronaut server application that is written in Java and tested with JUnit 5 looks like:

```groovy
plugins {
    id 'io.micronaut.application' version '1.0.0.M3'
}

version "0.1"
group "com.example"

repositories {
    mavenCentral()
}

micronaut {
    version = "2.0.0.RC1"
}

dependencies {
    implementation("io.micronaut:micronaut-http-server-netty")
    runtimeOnly("ch.qos.logback:logback-classic")
    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

mainClassName = "example.Application"

test {
    useJUnitPlatform()
}
```

### GraalVM Native Image

If the JVM being used to build is GraalVM a `nativeImage` task is added that will build the native image using the currently active GraalVM distribution:

```
$./gradlew nativeImage
```
