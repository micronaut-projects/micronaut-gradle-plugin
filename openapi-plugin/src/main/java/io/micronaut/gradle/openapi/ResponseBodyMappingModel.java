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
 * A model that can be used to specify body mappings during OpenAPI generation.
 *
 * <p>An equivalent to Micronaut OpenAPI's ResponseBodyMapping but
 * without using classes from that dependency so that we can isolate
 * execution in workers.</p>
 */
public final class ResponseBodyMappingModel implements Serializable {

    /**
     * The response header name that triggers the change of response type.
     */
    private final String headerName;

    /**
     * The type in which will be used as the response type. The type must take
     * a single type parameter, which will be the original body.
     */
    private final String mappedBodyType;

    /**
     * Whether the mapped body type needs to be supplied list items as property.
     */
    private final boolean isListWrapper;

    /**
     * Whether the mapped response body type required validation.
     */
    private final boolean isValidated;

    /**
     * Create a response body mapping.
     *
     * @param headerName The response header name that triggers the change of response type.
     * @param mappedBodyType The type in which will be used as the response type. The type must take
     *                       a single type parameter, which will be the original body. If the value is null,
     *                       the header will be removed and body not changed.
     */
    public ResponseBodyMappingModel(String headerName, String mappedBodyType) {
        this(headerName, mappedBodyType, false, false);
    }

    private ResponseBodyMappingModel(String headerName, String mappedBodyType, boolean isListWrapper, boolean isValidated) {
        this.headerName = headerName;
        this.mappedBodyType = mappedBodyType;
        this.isListWrapper = isListWrapper;
        this.isValidated = isValidated;
    }

    /**
     * Specify the name of the response header that triggers mapping.
     *
     * @param headerName the name
     * @return new instance with the updated header name
     */
    public ResponseBodyMappingModel withHeaderName(String headerName) {
        return new ResponseBodyMappingModel(headerName, mappedBodyType, isListWrapper, isValidated);
    }

    /**
     * Specify the fully-qualified name of the body type that will be generated
     * as the response return type in case mapping is triggered.
     *
     * @param mappedBodyType the type
     * @return new instance with the updated mapped body type
     */
    public ResponseBodyMappingModel withMappedBodyType(String mappedBodyType) {
        return new ResponseBodyMappingModel(headerName, mappedBodyType, isListWrapper, isValidated);
    }

    /**
     * Specify whether the mapped body is a list wrapper.
     * Then the mapped body type needs to be supplied list items as property
     *
     * @param isListWrapper whether it is a list wrapper
     * @return new instance with the isListWrapper property specified
     */
    public ResponseBodyMappingModel withListWrapper(boolean isListWrapper) {
        return new ResponseBodyMappingModel(headerName, mappedBodyType, isListWrapper, isValidated);
    }

    /**
     * Specify whether the mapped response body type required validation.
     *
     * @param isValidated whether it should be validated
     * @return new instance with validation requirement set
     */
    public ResponseBodyMappingModel withValidated(boolean isValidated) {
        return new ResponseBodyMappingModel(headerName, mappedBodyType, isListWrapper, isValidated);
    }

    public String getHeaderName() {
        return headerName;
    }

    public String getMappedBodyType() {
        return mappedBodyType;
    }

    public boolean isListWrapper() {
        return isListWrapper;
    }

    public boolean isValidated() {
        return isValidated;
    }

}
