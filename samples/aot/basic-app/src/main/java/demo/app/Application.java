package demo.app;

import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.ApplicationContextCustomizer;
import io.micronaut.context.annotation.ContextConfigurer;
import io.micronaut.runtime.Micronaut;

@ContextConfigurer
public class Application implements ApplicationContextCustomizer {
    @Override
    public void customize(ApplicationContextBuilder builder) {
        System.out.println("Java configurer loaded");
        builder.deduceEnvironment(false);
    }

    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }
}
