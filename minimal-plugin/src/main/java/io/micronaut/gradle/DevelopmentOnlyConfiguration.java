package io.micronaut.gradle;

import org.apache.tools.ant.taskdefs.condition.Os;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;

public final class DevelopmentOnlyConfiguration {
    public static final String CONFIGURATION_DEVELOPMENT_ONLY = "developmentOnly";

    private DevelopmentOnlyConfiguration() {

    }

    public static Configuration createDevelopmentOnlyConfiguration(Project project) {
        ConfigurationContainer configurations = project.getConfigurations();
        Configuration developmentOnly = configurations.create(CONFIGURATION_DEVELOPMENT_ONLY, conf -> {
            conf.setCanBeConsumed(false);
            conf.setCanBeResolved(true);
            conf.extendsFrom(configurations.getByName(MicronautComponentPlugin.MICRONAUT_BOMS_CONFIGURATION));
        });

        // added to ensure file watch works more efficiently on OS X
        if (Os.isFamily(Os.FAMILY_MAC)) {
            developmentOnly.getDependencies().add(project.getDependencies().create("io.micronaut:micronaut-runtime-osx"));
        }

        return developmentOnly;
    }
}
