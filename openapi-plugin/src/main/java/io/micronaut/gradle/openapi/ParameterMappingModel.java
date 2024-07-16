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
package io.micronaut.gradle.openapi;

import java.io.Serializable;

/**
 * A model that can be used to specify parameter mappings during the OpenAPI generation
 *
 * <p>An equivalent to Micronaut OpenAPI's ParameterMapping but
 * without using classes from that dependency so that we can isolate
 * execution in workers.</p>
 */
public final class ParameterMappingModel implements Serializable {

    /**
     * The name of the parameter as described by the name field in specification.
     */
    private final String name;

    /**
     * The location of parameter. Path parameters cannot be mapped, as this
     * behavior should not be used.
     */
    private final ParameterLocation location;

    /**
     * The type to which the parameter should be mapped. If multiple parameters
     * have the same mapping, only one parameter will be present. If set to null,
     * the original parameter will simply be deleted. The type should contain
     * the package name if required.
     */
    private final String mappedType;

    /**
     * The unique name of the parameter to be used as method parameter name.
     * By default, a name deduced from the parameter type will be used.
     */
    private final String mappedName;

    /**
     * Whether the mapped parameter requires validation.
     */
    private final boolean isValidated;

    /**
     * Create a parameter mapping
     *
     * @param name The name of the parameter as described by the name field in specification.
     * @param location The location of parameter. Path parameters cannot be mapped, as this
     *                 behavior should not be used.
     * @param mappedType The type to which the parameter should be mapped. If multiple parameters
     *                   have the same mapping, only one parameter will be present. If set to null,
     *                   the original parameter will simply be deleted. The type should contain
     *                   the package name if required.
     */
    public ParameterMappingModel(String name, ParameterLocation location, String mappedType) {
        this(name, location, mappedType, null, false);
    }

    private ParameterMappingModel(String name, ParameterLocation location, String mappedType, String mappedName, boolean isValidated) {
        this.name = name;
        this.location = location;
        this.mappedType = mappedType;
        this.mappedName = mappedName;
        this.isValidated = isValidated;
    }

    /**
     * Specify the name of the parameter to trigger the mapping.
     * The name should correspond to a field name in the OpenAPI specification.
     *
     * @param name the name
     * @return new instance with the updated name
     */
    public ParameterMappingModel withName(String name) {
        return new ParameterMappingModel(name, location, mappedType, mappedName, isValidated);
    }

    /**
     * Specify the location of the parameter that triggers the mapping.
     *
     * @param location the name
     * @return new instance with the updated location
     */
    public ParameterMappingModel withLocation(ParameterLocation location) {
        return new ParameterMappingModel(name, location, mappedType, mappedName, isValidated);
    }

    /**
     * Specify the fully-qualified type to which the parameter will be mapped .
     *
     * @param mappedType the type
     * @return new instance with the updated mapped type
     */
    public ParameterMappingModel withMappedType(String mappedType) {
        return new ParameterMappingModel(name, location, mappedType, mappedName, isValidated);
    }

    /**
     * Specify the unique name of the parameter to be used as method parameter name.
     *
     * @param mappedName the name
     * @return new instance with the name specified
     */
    public ParameterMappingModel withMappedName(String mappedName) {
        return new ParameterMappingModel(name, location, mappedType, mappedName, isValidated);
    }

    /**
     * Specify whether the parameter requires validation.
     *
     * @param isValidated the value
     * @return new instance with the validation requirement specified
     */
    public ParameterMappingModel withValidated(boolean isValidated) {
        return new ParameterMappingModel(name, location, mappedType, mappedName, isValidated);
    }

    public String getName() {
        return name;
    }

    public ParameterLocation getLocation() {
        return location;
    }

    public String getMappedType() {
        return mappedType;
    }

    public String getMappedName() {
        return mappedName;
    }

    public boolean isValidated() {
        return isValidated;
    }

    /**
     * Enum used to describe the location of a parameter.
     */
    public enum ParameterLocation {
        HEADER,
        QUERY,
        FORM,
        COOKIE,
        BODY
    }

}
