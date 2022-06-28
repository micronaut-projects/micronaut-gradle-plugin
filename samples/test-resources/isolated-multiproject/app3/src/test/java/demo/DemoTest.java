package demo;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import io.micronaut.context.annotation.Value;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
class DemoTest {

    @Value("${greeting.message}")
    String greeting;

    @Test
    void testItWorks() {
        assertEquals("Hello from my test resource!", greeting);
    }

}
