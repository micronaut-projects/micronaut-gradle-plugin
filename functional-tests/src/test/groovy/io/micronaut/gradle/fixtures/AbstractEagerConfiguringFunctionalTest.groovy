package io.micronaut.gradle.fixtures

import org.gradle.testkit.runner.BuildResult

class AbstractEagerConfiguringFunctionalTest extends AbstractFunctionalTest {
    @Override
    BuildResult build(String... args) {
        return super.build("tasks", *args)
    }


    String getGraalVMBuilderExports() {
        ["org.graalvm.nativeimage.builder/com.oracle.svm.core.jdk",
         "org.graalvm.nativeimage.builder/com.oracle.svm.core.configure",
         "org.graalvm.sdk/org.graalvm.nativeimage.impl"
        ].collect {
            "-J--add-exports=$it=ALL-UNNAMED"
        }.join(" ")
    }
}
