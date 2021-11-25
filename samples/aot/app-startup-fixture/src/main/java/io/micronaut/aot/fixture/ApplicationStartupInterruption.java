package io.micronaut.aot.fixture;

import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.ApplicationContextConfigurer;
import io.micronaut.context.annotation.ContextConfigurer;

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
            System.exit(0);
        }
    }
}
