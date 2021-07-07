plugins {
    `java-gradle-plugin`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.eclipse.jgit:org.eclipse.jgit:5.9.0.202009080501-r")
}

gradlePlugin {
    val git by plugins.creating {
        id = "io.micronaut.build.git-helper"
        implementationClass = "io.micronaut.build.GitHelperPlugin"
        description = "Helper to include git repositories as included builds"
    }
}
