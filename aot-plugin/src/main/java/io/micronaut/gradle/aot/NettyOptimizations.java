/*
 * Copyright 2003-2021 the original author or authors.
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
package io.micronaut.gradle.aot;

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

public interface NettyOptimizations {
    /**
     * Determines if Netty optimizations are enabled.
     * @return the enabled property
     */
    @Input
    Property<Boolean> getEnabled();

    /**
     * If Netty optimizations are optimized, you can set
     * this property to a fixed machine ID instead of Micronaut AOT
     * computing a random ID at runtime (not recommended).
     * If you set the value to "netty", then Netty will determine
     * the machine id at runtime, effectively disabling this optimization.
     * @return the machine ID
     */
    @Input
    @Optional
    Property<String> getMachineId();

    /**
     * If Netty optimizations are optimized, you can set
     * this property to a fixed PID instead of Micronaut AOT
     * computing a random ID at runtime (not recommended).
     * If you set the value to "netty", then Netty will determine
     * the PID at runtime, effectively disabling this optimization.
     * @return the machine ID
     */
    @Input
    @Optional
    Property<String> getPid();

}
