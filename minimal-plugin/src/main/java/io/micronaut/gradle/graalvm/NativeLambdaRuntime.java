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
package io.micronaut.gradle.graalvm;

public enum NativeLambdaRuntime {
    API_GATEWAY_V1("io.micronaut.function.aws.runtime.MicronautLambdaRuntime"),
    API_GATEWAY_V2("io.micronaut.function.aws.runtime.APIGatewayV2HTTPEventMicronautLambdaRuntime"),
    ALB("io.micronaut.function.aws.runtime.ApplicationLoadBalancerMicronautLambdaRuntime");

    private final String mainClassName;

    NativeLambdaRuntime(String mainClassName) {

        this.mainClassName = mainClassName;
    }

    public String getMainClassName() {
        return mainClassName;
    }
}
