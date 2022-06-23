package demo;

import io.micronaut.testresources.core.TestResourcesResolver;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

public class GreetingTestResource implements TestResourcesResolver {

    public static final String PROPERTY = "greeting.message";

    @Override
    public List<String> getResolvableProperties(Map<String, Collection<String>> propertyEntries, Map<String, Object> testResourcesConfig) {
        return Collections.singletonList(PROPERTY);
    }

    @Override
    public Optional<String> resolve(String propertyName, Map<String, Object> properties, Map<String, Object> testResourcesConfiguration) {
        if (PROPERTY.equals(propertyName)) {
            return Optional.of(StringUtils.capitalize("hello from my test resource!"));
        }
        return Optional.empty();
    }

}
