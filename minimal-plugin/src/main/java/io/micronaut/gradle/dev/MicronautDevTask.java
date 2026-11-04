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

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.ExecResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs {@code io.micronaut.dev.MicronautDevMain}, {@code ./gradlew mnDev}. The build's {@code micronaut.dev.*} system
 * properties reach the launcher as system properties, which override the entries of the manifest, and a launcher that
 * exits with {@value #RELAUNCH}, because its generation budget is spent, is started again.
 *
 * @since 5.0.3
 */
public abstract class MicronautDevTask extends JavaExec {

    /**
     * The status of {@code MicronautDevMain.RELAUNCH}: the runtime closed for whoever started the launcher to start it
     * again, from the classes the last change compiled.
     */
    static final int RELAUNCH = 3;

    private static final Logger LOGGER = Logging.getLogger(MicronautDevTask.class);

    /**
     * Passes the overrides to the launcher.
     */
    public MicronautDevTask() {
        getJvmArgumentProviders().add(new Overrides(getOverrides()));
    }

    /**
     * The {@code micronaut.dev.*} system properties of the build, passed on to the launcher.
     *
     * @return the properties
     */
    @Input
    public abstract MapProperty<String, String> getOverrides();

    @Override
    @TaskAction
    public void exec() {
        boolean ignoreExitValue = isIgnoreExitValue();
        setIgnoreExitValue(true);
        ExecResult result;
        while (true) {
            super.exec();
            result = getExecutionResult().get();
            if (result.getExitValue() != RELAUNCH) {
                break;
            }
            LOGGER.lifecycle("The generation budget of the development process is spent: starting it again");
        }
        setIgnoreExitValue(ignoreExitValue);
        if (!ignoreExitValue) {
            result.assertNormalExitValue();
        }
    }

    /**
     * The overrides as system properties of the launcher.
     */
    private record Overrides(MapProperty<String, String> overrides) implements CommandLineArgumentProvider {

        @Override
        public Iterable<String> asArguments() {
            List<String> arguments = new ArrayList<>();
            overrides.get().forEach((key, value) -> arguments.add("-D" + key + "=" + value));
            return arguments;
        }
    }
}
