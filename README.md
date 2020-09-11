# Micronaut Gradle Plugin

A Gradle Plugin for Micronaut that configures the Micronaut BOM and sets up annotation processing.

This project consists of 2 plugins: one for applications and one for Micronaut libraries.

## Micronaut Library Plugin

```groovy
plugins {
  id "io.micronaut.library" version "{version}"
}
```

The [Micronaut library plugin](https://plugins.gradle.org/plugin/io.micronaut.library) applies the following modifications to the build:

* Applies the [Micronaut Bill of Materials (BOM)](https://search.maven.org/artifact/io.micronaut/micronaut-bom)
* Applies the `java-library` plugin
* Configures annotation processing for the current language (Groovy, Java or Kotlin)

The `micronaut` DSL can be used to configure how this behaves.

The minimum requirement is to set the Micronaut version to use. This can be done by setting `micronautVersion` in `gradle.properties` or as follows in `build.gradle`:

```groovy
micronaut {
    version "2.0.1"
}
```

Complete example with the default settings:

```groovy

micronaut {
    version "2.0.1"
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
        annotations "com.example.*"
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

With the `io.micronaut.library` plugin applied a minimal build to get started writing a library for Micronaut that written in Java and is tested with JUnit 5 looks like:

```groovy
plugins {
    id 'io.micronaut.library' version '{version}'
}

version "0.1"
group "com.example"

repositories {
    mavenCentral()
}

micronaut {
    version = "2.0.1"
}

dependencies {
    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}
```

## Micronaut Application Plugin

```groovy
plugins {
  id "io.micronaut.application" version "{version}"
}
```

The [Micronaut application plugin](https://plugins.gradle.org/plugin/io.micronaut.application) extends the Micronaut Library plugin and adds the following customizations:

* Instead of the `java-library` plugin the plugin applies the Gradle `application` plugin.
* If the current JVM is GraalVM configures a `nativeImage` task
* Correctly configures Gradle for continuous build

The following additional tasks are provided by this plugin:

* `buildLayers` - Builds application layers for use in a Docker container
* `dockerfile` - Builds a Docker File for a Micronaut application
* `dockerBuild` - Builds a Docker Image using the [Docker Gradle plugin](https://github.com/bmuschko/gradle-docker-plugin)
* `dockerfileNative` - Builds a Docker File for for GraalVM Native Image
* `dockerBuildNative` - Builds a Native Docker Image using GraalVM Native Image
* `nativeImage` - Builds a GraalVM Native Image
* `dockerPush` - Pushes a Docker Image to configured container registry
* `dockerPushNative` - Pushes a Docker Image built with GraalVM Native Image to configured container registry

To run an application with continuous build use the `run` task with the `-t` parameter:

```bash
$ ./gradlew run -t
```

### Minimal Build

With the `io.micronaut.application` plugin applied a minimal build to get started with a Micronaut server application that is written in Java and tested with JUnit 5 looks like:

```groovy
plugins {
    id 'io.micronaut.application' version '{version}'
}

version "0.1"
group "com.example"

repositories {
    mavenCentral()
}

micronaut {
    version = "2.0.1"
}

dependencies {
    implementation("io.micronaut:micronaut-http-server-netty")
    runtimeOnly("ch.qos.logback:logback-classic")
    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

mainClassName = "example.Application"
```

### Kotlin Support

The most simple Kotlin build using a `build.gradle.kts` file looks like:

```kotlin
plugins {
    id("org.jetbrains.kotlin.jvm") version "1.3.72"
    id("org.jetbrains.kotlin.kapt") version "1.3.72"
    id("org.jetbrains.kotlin.plugin.allopen") version "1.3.72"    
    id("io.micronaut.application") version "{version}"
}

version "0.1"
group "com.example"

repositories {
    mavenCentral()
}

micronaut {
    version( "2.0.1")
}

dependencies {
    implementation("io.micronaut:micronaut-http-server-netty")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.3.72")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.3.72")
    runtimeOnly("ch.qos.logback:logback-classic")
    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

application {
    mainClass.set("example.ApplicationKt")
}
```

### GraalVM Native Image

If the JVM being used to build is a GraalVM SDK with the `native-image` tool enabled a `nativeImage` task is added that will build the native image using the currently active GraalVM distribution:

```
$./gradlew nativeImage
```

To configure Native Image arguments you can use the `args` method of the `nativeImage` task:

```groovy
nativeImage {
    args('--static')
}
```

### Docker Support

The Micronaut plugin includes integration with the [Gradle Docker plugin](https://bmuschko.github.io/gradle-docker-plugin) allowing you to easily build applications and native images using Docker containers.

Applications are built as layered JARs using the `buildLayers` task ensuring optimized Docker images for Java applications.

To build a regular Java application into a Docker container that is ready to be deployed and exposes ports 8080 you can simply do:

```bash
$ ./gradlew dockerBuild
``` 

The default uses an OpenJDK 14 slim base image, however you can easily switch the base image to use by using the `baseImage` property of the `dockerfile` task:

```groovy
dockerfile {
  baseImage = "oracle/graalvm-ce:20.2.0-java11"
}
```

The above examples switches to use GraalVM CE 20.2.0 as a base image.

To build the application into a Native Image you can run:

```groovy
$ ./gradlew dockerBuildNative
```

Note that for this to work you must build the application with the same GraalVM SDK as used to build the image.

To push the container to the currently configure configured container registry you can use either `dockerPush` or `dockerPushNative` for the native image:

```bash
$ ./gradlew dockerPush
```

To configure the image names to push you can use the `images` setting of the `dockerBuild` task.

For example the following configures `dockerPush` to use Oracle Container Registry:

```groovy
dockerBuild {
    images = ["eu-frankfurt-1.ocir.io/xyzzyz/repo/my-image:$project.version"]
}

dockerBuildNative {
    images = ["eu-frankfurt-1.ocir.io/xyzzyz/repo/my-image-native:$project.version"]
}
```

Notice that you can supply two different image names to push to for the JVM version and the native version of the application.

If you wish to customize the docker builds that are used, the easiest way is to run `./gradlew dockerfile` (or `dockerfileNative` for the native versrion) and copy the generated `Dockerfile` from `build/docker` to your root directory and modify as required.

If you wish to customize the JVM arguments or native image arguments then it is possible to do so with the `args` method of the `dockerfile` and `dockerfileNative` tasks:

```groovy
dockerfile {
 args("-Xmx128m")
}
dockerfileNative {
 args("-Xmx64m")
}
```

The above configuration uses a max heap setting of 128m for Java and 64m for native image for the application. 

### Micronaut Runtimes

A higher level concept of "runtimes" is included in the Micronaut Gradle plugin which essentially allows the plugin to decide which server runtime to include in the dependencies of the application when building the application. For example consider this minimal build:

```groovy
plugins {
     id 'io.micronaut.application' version '{version}'
}
version "0.1"
group "com.example"

repositories {
    jcenter()
    mavenCentral()
}

micronaut {
    version = "2.0.1"
    runtime "netty"
}

dependencies {
    runtimeOnly("ch.qos.logback:logback-classic")
}

mainClassName = "example.Application"
```

Here the only dependency declared is on the logging framework to use however `runtime` is to `netty` resulting in an application that can be built and run.

If you wish to take the same and build or run it with a different runtime you can pass the `micronaut.runtime` property for the build. For example:

```bash
./gradlew run -Pmicronaut.runtime=google_function
``` 

The above example run the application as a Google Cloud Function.

The available runtimes are:

* `netty` - A Netty server runtime
* `jetty` - A Jetty server runtime
* `tomcat` - A Tomcat server runtime
* `undertow` - An Undertow server runtime
* `lambda` - Allows building the application into an AWS Lambda
* `lambda_native` - Allows building the applciation into a native AWS Lambda with GraalVM
* `oracle_function` - A Project.fn runtime for deploying Oracle Functions
* `google_function` - A runtime for deploying Google Functions.
* `azure_function` - A runtime for deploying Azure Functions

The advantage of allowing your dependencies to be dictated by the runtime is that you can potentially take the same application and deploy it to any of the above runtimes without changes.
