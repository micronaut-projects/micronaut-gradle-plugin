package io.micronaut.gradle;

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.TaskContainer;

import java.util.LinkedHashMap;
import java.util.Map;

public class MicronautApplicationPlugin extends MicronautLibraryPlugin {
    @Override
    public void apply(Project project) {
        super.apply(project);

        Configuration developmentOnly = project
                .getConfigurations().create("developmentOnly");


        final TaskContainer tasks = project.getTasks();
        tasks.withType(JavaExec.class, javaExec -> {
            javaExec.getClasspath().plus(developmentOnly.getAsFileTree());
            if (project.getGradle().getStartParameter().isContinuous()) {
                Map<String, Object> sysProps = new LinkedHashMap<>();
                sysProps.put("micronaut.io.watch.restart", true);
                sysProps.put("micronaut.io.watch.enabled", true);
                sysProps.put("micronaut.io.watch.paths", "src/main");
                javaExec.systemProperties(
                        sysProps
                );
            }
        });

        tasks.withType(ShadowJar.class, ShadowJar::mergeServiceFiles);
    }

    @Override
    protected String getBasePluginName() {
        return "application";
    }
}
