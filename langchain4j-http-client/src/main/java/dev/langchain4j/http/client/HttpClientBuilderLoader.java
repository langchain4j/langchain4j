package dev.langchain4j.http.client;

import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class HttpClientBuilderLoader {

    public static HttpClientBuilder loadHttpClientBuilder() {
        String selectedClassName = System.getProperty("langchain4j.http.clientBuilderFactory");

        Collection<HttpClientBuilderFactory> factories = loadFactories(HttpClientBuilderFactory.class);
        if (factories.isEmpty()) {
            throw new IllegalStateException("No HTTP client has been found in the classpath");
        }
        if (selectedClassName != null) {
            for (HttpClientBuilderFactory factory : factories) {
                if (selectedClassName.equals(factory.getClass().getName())) {
                    return factory.create();
                }
            }
            throw new IllegalStateException(String.format(
                    "The value of the `langchain4j.http.clientBuilderFactory` system property does not match any of the available HTTP Clients in the classpath: %s.",
                    factoryNames(factories)));
        }

        int highestPriority = factories.stream()
                .mapToInt(HttpClientBuilderFactory::priority)
                .max()
                .orElseThrow();
        List<HttpClientBuilderFactory> preferredFactories = factories.stream()
                .filter(factory -> factory.priority() == highestPriority)
                .toList();
        if (preferredFactories.size() > 1) {
            throw new IllegalStateException(String.format(
                    "Conflict: multiple HTTP clients have been found with the highest priority "
                            + "in the classpath: %s. Please explicitly specify the one you wish to use using the `langchain4j.http.clientBuilderFactory` system property.",
                    factoryNames(preferredFactories)));
        }
        return preferredFactories.get(0).create();
    }

    private static Set<String> factoryNames(Collection<HttpClientBuilderFactory> factories) {
        return factories.stream().map(f -> f.getClass().getName()).collect(Collectors.toSet());
    }
}
