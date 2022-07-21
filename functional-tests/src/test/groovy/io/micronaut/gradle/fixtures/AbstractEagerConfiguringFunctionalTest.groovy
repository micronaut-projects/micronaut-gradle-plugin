package io.micronaut.gradle.fixtures

import org.gradle.testkit.runner.BuildResult

class AbstractEagerConfiguringFunctionalTest extends AbstractFunctionalTest {
    @Override
    BuildResult build(String... args) {
        return super.build("tasks", *args)
    }


    String getGraalVMBuilderExports() {
        ["com.oracle.svm.core.jdk",
         "com.oracle.svm.core.configure"].collect {
            "-J--add-exports=org.graalvm.nativeimage.builder/$it=ALL-UNNAMED"
        }.join(" ")
    }
}
