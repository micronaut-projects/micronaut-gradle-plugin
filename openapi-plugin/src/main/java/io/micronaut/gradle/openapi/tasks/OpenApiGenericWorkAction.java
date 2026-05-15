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
package io.micronaut.gradle.openapi.tasks;

import io.micronaut.openapi.generator.GeneratorOptionsBuilder;
import io.micronaut.openapi.generator.MicronautCodeGenerator;
import io.micronaut.openapi.generator.MicronautCodeGeneratorBuilder;
import org.gradle.api.GradleException;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;

public abstract class OpenApiGenericWorkAction extends AbstractOpenApiWorkAction<OpenApiGenericWorkAction.GenericParameters> {

    protected interface GenericParameters extends OpenApiParameters {

        Property<String> getGeneratorClassName();

        MapProperty<String, Object> getGeneratorProperties();
    }

    @Override
    protected void configureBuilder(MicronautCodeGeneratorBuilder builder) {
        var params = getParameters();
        builder.forCodeGenerator(instantiateGenerator(params.getGeneratorClassName().get()), config -> {
            for (Map.Entry<String, Object> entry : params.getGeneratorProperties().getOrElse(Map.of()).entrySet()) {
                invokeMethod(entry.getKey(), config, entry.getValue());
            }
        });
    }

    @SuppressWarnings("unchecked")
    private MicronautCodeGenerator<? extends GeneratorOptionsBuilder> instantiateGenerator(String generatorClassName) {
        try {
            var constructor = getClass()
                .getClassLoader()
                .loadClass(generatorClassName)
                .getDeclaredConstructor();
            constructor.setAccessible(true);
            return (MicronautCodeGenerator<? extends GeneratorOptionsBuilder>) constructor.newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new GradleException("Unable to instantiate OpenAPI generator '" + generatorClassName + "'", e);
        }
    }

    private static void invokeMethod(String name, GeneratorOptionsBuilder builder, Object value) {
        String capitalizedName = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        String witherName = "with" + capitalizedName;
        String setterName = "set" + capitalizedName;
        Class<? extends GeneratorOptionsBuilder> builderClass = builder.getClass();
        try {
            for (Method method : builderClass.getMethods()) {
                if (invokeIfMatches(name, builder, value, witherName, setterName, method)) {
                    return;
                }
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new GradleException("Unable to configure OpenAPI generator property '" + name + "'", e);
        }
        throw new GradleException("Unable to find a method on builder " + builderClass + " with name '" + name + "' accepting a value of type '" + valueType(value) + "'. Supported value types are String, Boolean, and Integer.");
    }

    private static boolean invokeIfMatches(
        String name,
        GeneratorOptionsBuilder builder,
        Object value,
        String witherName,
        String setterName,
        Method method
    ) throws IllegalAccessException, InvocationTargetException {
        var methodName = method.getName();
        if ((methodName.equals(name) || methodName.equals(witherName) || methodName.equals(setterName)) && method.getParameterCount() == 1) {
            Object argument = coerceArgument(method.getParameterTypes()[0], value);
            if (argument != null) {
                method.setAccessible(true);
                method.invoke(builder, argument);
                return true;
            }
        }
        return false;
    }

    private static Object coerceArgument(Class<?> parameterType, Object value) {
        if (value == null) {
            return null;
        }
        if (parameterType.isInstance(value)) {
            return value;
        }
        if (parameterType.equals(Boolean.TYPE) && value instanceof Boolean) {
            return value;
        }
        if (parameterType.equals(Integer.TYPE) && value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String stringValue) {
            if (parameterType.equals(String.class)) {
                return stringValue;
            }
            if (parameterType.equals(Boolean.TYPE)) {
                var coerced = stringValue.toLowerCase(Locale.US);
                if ("true".equals(coerced) || "false".equals(coerced)) {
                    return Boolean.parseBoolean(coerced);
                }
            }
            if (parameterType.equals(Integer.TYPE) && stringValue.matches("[0-9]+")) {
                return Integer.parseInt(stringValue);
            }
        }
        return null;
    }

    private static String valueType(Object value) {
        return value == null ? "null" : value.getClass().getName();
    }
}
