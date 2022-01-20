package io.micronaut.aot.fixture;

import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.ApplicationContextConfigurer;
import io.micronaut.context.annotation.ContextConfigurer;

import java.util.Map;
import java.lang.reflect.Field;

/**
 * A context configurer for tests which is simply aimed at shutting down
 * the application under test.
 */
@ContextConfigurer
public class ApplicationStartupInterruption implements ApplicationContextConfigurer {
    @Override
    public int getOrder() {
        return LOWEST_PRECEDENCE;
    }

    public @Override
    void configure(ApplicationContextBuilder builder) {
        if (System.getProperty("io.micronaut.internal.test.interrupt.startup") != null) {
            System.out.println("Detected test, interrupting application startup");
            try {
                Class<?> clazz = Class.forName("io.micronaut.core.optim.StaticOptimizations");
                Field field = clazz.getDeclaredField("OPTIMIZATIONS");
                field.setAccessible(true);
                Map<Class<?>, Object> optimizations = (Map<Class<?>, Object>) field.get(null);
                optimizations.keySet().forEach(c -> System.out.println("Setting optimizations for " + c));
            } catch (Exception ex) {
                // ignore
            }
            System.exit(0);
        }
    }
}
