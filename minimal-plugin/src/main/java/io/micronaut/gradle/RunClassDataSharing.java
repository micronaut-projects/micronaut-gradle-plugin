/*
 * Copyright 2003-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.gradle;

import org.gradle.api.Incubating;
import org.gradle.api.provider.Property;

/**
 * Class data sharing (CDS) for the {@code run} task, configured with
 * {@code micronaut { runClassDataSharing { } }}.
 *
 * <p>When it is enabled and the launcher of the {@code run} task is JDK 25 or later, the task puts the
 * JARs of the external dependencies first on its class path, and the project's own classes, resources
 * and sibling projects after them. The first run after a dependency change records the classes that it
 * loads. The next one dumps a CDS archive of the dependency JARs from that list, checks it once, and
 * launches the application with it. The archive is a local file under the build directory: it is never
 * stored in a build cache.</p>
 *
 * @since 5.1.0
 */
@Incubating
public interface RunClassDataSharing {

    /**
     * Whether the {@code run} task uses a CDS archive of the dependency JARs. Defaults to {@code false}.
     * It needs a launcher of JDK 25 or later. With an older launcher, the {@code run} task starts as it
     * does without this setting.
     *
     * @return the enabled property
     */
    Property<Boolean> getEnabled();

    /**
     * Whether the archive is dumped with {@code -XX:+AOTClassLinking}, which also links the archived
     * classes ahead of time. Defaults to {@code false}. It applies only where an AOT-linked archive can be
     * used: on JDK 25 and 26, and for launches without a debugger, an agent or a module option that opens,
     * exports or patches modules. Other launches use a plain archive.
     *
     * @return the AOT class linking property
     */
    Property<Boolean> getAotClassLinking();
}
