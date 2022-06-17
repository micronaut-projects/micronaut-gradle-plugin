
package io.micronaut.internal.build.test

import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.SetProperty

import javax.inject.Inject

abstract class FunctionalTestingExtension {
    @Inject
    abstract DependencyHandler getDependencies()

    @Inject
    abstract ObjectFactory getObjects()

    abstract SetProperty<String> getPlugins()

    void pluginUnderTest(String alias) {
        plugins.add(alias)
        dependencies.add("pluginsUnderTest", dependencies.create(
                dependencies.project(path: ":micronaut-${alias}-plugin", configuration: 'repository')
        ))

        ProjectDependency projectDependency = dependencies.project(path: ":micronaut-${alias}-plugin")
        dependencies.add("jacocoAdditionalClasses", dependencies.create(projectDependency))
        dependencies.add("jacocoAdditionalSources", dependencies.create(projectDependency))
    }
}
