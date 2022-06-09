package io.micronaut.gradle;

import com.diffplug.gradle.eclipse.apt.AptEclipsePlugin;
import io.micronaut.gradle.graalvm.MicronautGraalPlugin;
import io.micronaut.gradle.testresources.MicronautTestResourcesPlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.PluginContainer;

/**
 * A plugin for creating a Micronaut library. Applies the java-library plugin by default.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class MicronautLibraryPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        final PluginContainer plugins = project.getPlugins();

        plugins.apply(MicronautMinimalLibraryPlugin.class);
        plugins.apply(AptEclipsePlugin.class);
        plugins.apply(MicronautGraalPlugin.class);
        plugins.apply(MicronautTestResourcesPlugin.class);

    }

}
