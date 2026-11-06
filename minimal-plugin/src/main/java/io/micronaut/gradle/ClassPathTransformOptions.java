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
package io.micronaut.gradle;

import org.gradle.api.Incubating;
import org.gradle.api.provider.Property;

/**
 * Rewrites the class files of the packaged class path with Micronaut AOT's {@code micronaut-aot-bytecode}.
 * It applies to the {@code shadowJar} and {@code optimizedJitJarAll} fat JARs and to the JVM Docker images,
 * never to the {@code run} tasks, tests or native images. Both steps are off by default.
 *
 * @since 5.1.0
 */
@Incubating
public interface ClassPathTransformOptions {

    /**
     * Whether the lambdas of the application and of its dependencies are desugared into classes generated at
     * build time. It makes starts without a JDK AOT cache faster. Defaults to {@code false}.
     *
     * @return the desugar lambdas property
     */
    Property<Boolean> getDesugarLambdas();

    /**
     * Whether the local-variable tables of the third-party JARs are stripped. It makes starts without a JDK AOT
     * cache slightly faster, and drops variable names from debuggers and from helpful
     * {@code NullPointerException} messages for those JARs. Defaults to {@code false}.
     *
     * @return the strip local variables property
     */
    Property<Boolean> getStripLocalVariables();

    /**
     * The version of {@code io.micronaut.aot:micronaut-aot-bytecode} that rewrites the class path.
     *
     * @return the version
     */
    Property<String> getVersion();
}
