# Micronaut Gradle Plugin

A Gradle Plugin for Micronaut that configures the Micronaut BOM and sets up annotation processing.

This project consists of 2 plugins: one for applications and one for Micronaut libraries.

## Micronaut Library Plugin

```groovy
plugins {
  id "io.micronaut.library" version "{version}"
}
```

The Micronaut library plugin sets up basic annotation processing and applies the `java-library` plugin. The `micronaut` DSL can be used to configure how this behaves. Complete example with the default settings:

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
        // Sets the packages containing in any custom Micronaut 
        // meta annotations (new annotations annotated with say @Around). 
        // Generally used only for advanced cases such as defining new AOP
        // advice. If omitted however, incremental annotation processing
        // will not work correctly
        anntations "com.example.*"
    }   
}
```
## Micronaut Application Plugin

```groovy
plugins {
  id "io.micronaut.application" version "{version}"
}
```

The Micronaut application plugin extends the Micronaut Library plugin and instead applies the Gradle `application` plugin as well.

In addition, the plugin correctly configures the `run` task so it can be used with continuous build:

```bash
$ ./gradlew run --continuous --watch-fs
```