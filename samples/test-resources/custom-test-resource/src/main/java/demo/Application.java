package demo;

import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.ApplicationContextConfigurer;
import io.micronaut.context.annotation.ContextConfigurer;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.context.annotation.Value;
import io.micronaut.runtime.Micronaut;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collections;

@Singleton
public class Application {

    private final String greeting;

    public Application(@Value("${greeting.message}") String greeting) {
        this.greeting = greeting;
    }

    @ContextConfigurer
    public static class Configurer implements ApplicationContextConfigurer {
        @Override
        public void configure(ApplicationContextBuilder builder) {
            builder.deduceEnvironment(false);
            builder.banner(false);
        }
    }

    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
        if (System.getProperty("interruptStartup") != null) {
            System.exit(0);
        }
    }

    @EventListener
    public void onStart(StartupEvent event) {
        System.out.println(greeting);
    }
}
