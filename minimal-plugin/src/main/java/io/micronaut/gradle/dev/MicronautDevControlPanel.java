/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.gradle.dev;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Provider;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Micronaut Control Panel in development mode: the modules {@code mnDev} adds to the development runtime classpath,
 * the panels inferred from the modules the application uses, and the defaults the panel reads with the lowest
 * precedence, so that the application's own configuration and system properties win.
 *
 * <p>The panels mirror Pyronaut's {@code ControlPanelFeature}: a panel is added when the application resolves one of
 * the modules it inspects.</p>
 */
final class MicronautDevControlPanel {

    /**
     * The Gradle property that turns Control Panel on or off, over the {@code micronaut.dev.controlPanel} DSL.
     */
    static final String ENABLED_PROPERTY = "micronaut.dev.control-panel";

    /**
     * The group of every Control Panel module.
     */
    static final String GROUP = "io.micronaut.controlpanel";

    /**
     * The modules added whatever the application uses: the UI, the management panels and the testing panels.
     */
    static final List<String> BASE_MODULES = List.of(
        GROUP + ":micronaut-control-panel-ui",
        GROUP + ":micronaut-control-panel-management",
        GROUP + ":micronaut-control-panel-testing"
    );

    /**
     * The defaults of development mode, read by Control Panel from its {@code micronaut-control-panel.properties}
     * resources as the property source of the lowest precedence.
     */
    static final Map<String, String> DEFAULTS;

    /**
     * The resource Control Panel reads its defaults from.
     */
    static final String DEFAULTS_RESOURCE = "micronaut-control-panel/micronaut-control-panel.properties";

    static {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("micronaut.control-panel.enabled", "true");
        defaults.put("micronaut.control-panel.path", "/control-panel");
        defaults.put("micronaut.control-panel.security.access", "ANONYMOUS");
        DEFAULTS = java.util.Collections.unmodifiableMap(defaults);
    }

    private static final Logger LOGGER = Logging.getLogger(MicronautDevControlPanel.class);

    private MicronautDevControlPanel() {
    }

    /**
     * The optional panels, each added when the application resolves one of its trigger modules.
     */
    enum Panel {
        DATASOURCE("micronaut-control-panel-datasource", List.of("io.micronaut.sql:micronaut-jdbc")),
        HIBERNATE("micronaut-control-panel-hibernate", List.of("org.hibernate.orm:hibernate-core")),
        KAFKA("micronaut-control-panel-kafka", List.of("io.micronaut.kafka:micronaut-kafka")),
        OBJECT_STORAGE("micronaut-control-panel-object-storage", List.of("io.micronaut.objectstorage:micronaut-object-storage-core")),
        CACHE("micronaut-control-panel-cache", List.of(
            "io.micronaut.cache:micronaut-cache-caffeine",
            "io.micronaut.cache:micronaut-cache-ehcache",
            "io.micronaut.cache:micronaut-cache-hazelcast",
            "io.micronaut.cache:micronaut-cache-infinispan"
        ));

        private final String artifactId;
        private final List<String> triggers;

        Panel(String artifactId, List<String> triggers) {
            this.artifactId = artifactId;
            this.triggers = triggers;
        }

        String moduleKey() {
            return GROUP + ":" + artifactId;
        }

        List<String> triggers() {
            return triggers;
        }

        /**
         * The panels the modules of an application call for.
         *
         * @param moduleKeys the resolved modules, as {@code group:name}
         * @return the panels
         */
        static Set<Panel> detect(Collection<String> moduleKeys) {
            Set<Panel> detected = EnumSet.noneOf(Panel.class);
            for (Panel panel : values()) {
                if (panel.triggers.stream().anyMatch(moduleKeys::contains)) {
                    detected.add(panel);
                }
            }
            return detected;
        }
    }

    /**
     * Whether Control Panel is on: the Gradle property if it is set, else the DSL.
     *
     * @param project the project
     * @param dev the extension
     * @return the flag
     */
    static Provider<Boolean> enabled(Project project, MicronautDevExtension dev) {
        return project.getProviders().gradleProperty(ENABLED_PROPERTY)
            .map(value -> Boolean.parseBoolean(value.trim()))
            .orElse(dev.getControlPanel().getEnabled());
    }

    /**
     * The modules Control Panel adds for an application: the base modules, then the panels of the modules on its
     * runtime classpath, without a version, which the Micronaut platform gives.
     *
     * @param moduleKeys the modules of the application, as {@code group:name}
     * @return the modules to add, as {@code group:name}
     */
    static List<String> modules(Collection<String> moduleKeys) {
        List<String> modules = new ArrayList<>(BASE_MODULES);
        for (Panel panel : Panel.detect(moduleKeys)) {
            modules.add(panel.moduleKey());
        }
        return modules;
    }

    /**
     * The dependencies on Control Panel for the modules a runtime classpath resolves.
     *
     * @param dependencies the dependency handler
     * @param runtimeClasspath the application's development runtime classpath
     * @return the dependencies
     */
    static List<Dependency> dependencies(DependencyHandler dependencies, Configuration runtimeClasspath) {
        Set<String> moduleKeys = new HashSet<>();
        for (ResolvedComponentResult component : runtimeClasspath.getIncoming().getResolutionResult().getAllComponents()) {
            ModuleVersionIdentifier id = component.getModuleVersion();
            if (id != null) {
                moduleKeys.add(id.getGroup() + ":" + id.getName());
            }
        }
        List<Dependency> result = new ArrayList<>();
        for (String module : modules(moduleKeys)) {
            result.add(dependencies.create(module));
        }
        return result;
    }

    /**
     * Whether an unresolved dependency is a Control Panel module, which development mode does without: a module that
     * is not published yet, such as the testing panels, or a platform that does not manage it.
     */
    static boolean isControlPanel(ComponentSelector selector) {
        return selector instanceof ModuleComponentSelector module && GROUP.equals(module.getGroup());
    }

    /**
     * The dependencies of a configuration that do not resolve, but the Control Panel modules, which development mode
     * does without: a callable provider, which the configuration cache stores as its value, the descriptions alone.
     *
     * @param project the project
     * @param configuration the configuration, resolved leniently by the task
     * @return the descriptions of the failures
     */
    static Provider<List<String>> unresolved(Project project, Configuration configuration) {
        return project.provider(() -> {
            List<String> failures = new ArrayList<>();
            List<String> skipped = new ArrayList<>();
            ArrayDeque<ResolvedComponentResult> queue = new ArrayDeque<>();
            Set<Object> seen = new HashSet<>();
            queue.add(configuration.getIncoming().getResolutionResult().getRootComponent().get());
            while (!queue.isEmpty()) {
                ResolvedComponentResult component = queue.poll();
                if (!seen.add(component.getId())) {
                    continue;
                }
                for (DependencyResult dependency : component.getDependencies()) {
                    if (dependency instanceof UnresolvedDependencyResult unresolved) {
                        String name = unresolved.getAttempted().getDisplayName();
                        if (isControlPanel(unresolved.getAttempted())) {
                            skipped.add(name);
                        } else {
                            failures.add(name + ": " + unresolved.getFailure().getMessage());
                        }
                    } else if (dependency instanceof ResolvedDependencyResult resolved) {
                        queue.add(resolved.getSelected());
                    }
                }
            }
            if (!skipped.isEmpty()) {
                LOGGER.info("Micronaut Control Panel modules not available for {}, development mode runs without them: {}", configuration.getName(), skipped);
            }
            return failures;
        });
    }

    /**
     * Fails the task when a dependency of its classpath does not resolve, unless it is a Control Panel module: the
     * classpaths of development mode are resolved leniently so that Control Panel modules may be missing.
     */
    static final class VerifyResolution implements Action<Task> {
        private final Provider<List<String>> failures;

        VerifyResolution(Provider<List<String>> failures) {
            this.failures = failures;
        }

        @Override
        public void execute(Task task) {
            List<String> failed = failures.get();
            if (!failed.isEmpty()) {
                throw new GradleException("Could not resolve the classpath of " + task.getPath() + ":\n  " + String.join("\n  ", failed));
            }
        }
    }
}
