package demo.app;

import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.ApplicationContextConfigurer;
import io.micronaut.context.annotation.ContextConfigurer;
import io.micronaut.runtime.Micronaut;

@ContextConfigurer
public class Application implements ApplicationContextConfigurer {
    @Override
    public void configure(ApplicationContextBuilder builder) {
        System.out.println("Java configurer loaded");
        builder.deduceEnvironment(false);
    }

    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }
}
