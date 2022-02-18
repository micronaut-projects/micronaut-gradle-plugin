package io.micronaut.internal.build.test

import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.provider.SetProperty

import javax.inject.Inject

abstract class FunctionalTestingExtension {
    @Inject
    abstract DependencyHandler getDependencies()

    abstract SetProperty<String> getPlugins()

    void pluginUnderTest(String alias) {
        plugins.add(alias)
        dependencies.add("pluginsUnderTest", dependencies.create(
                dependencies.project(path: ":micronaut-${alias}-plugin", configuration: 'repository')
        ))
    }
}
