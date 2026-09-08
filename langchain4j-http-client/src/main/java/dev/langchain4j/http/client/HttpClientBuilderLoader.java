package dev.langchain4j.http.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

public class HttpClientBuilderLoader {

    public static HttpClientBuilder loadHttpClientBuilder() {
        return loadHttpClientBuilder(loadFactories());
    }

    static HttpClientBuilder loadHttpClientBuilder(Collection<HttpClientBuilderFactory> factories) {
        String selectedClassName = System.getProperty("langchain4j.http.clientBuilderFactory");

        HttpClientBuilderFactory effectiveFactory = null;
        for (HttpClientBuilderFactory factory : factories) {
            if (effectiveFactory != null) {
                throw new IllegalStateException(String.format(
                        "Conflict: multiple HTTP clients have been found in the classpath: %s. Please explicitly"
                                + " specify the one you wish to use using the `langchain4j.http.clientBuilderFactory`"
                                + " system property.",
                        factoryNames(factories)));
            } else {
                if (selectedClassName == null) {
                    effectiveFactory = factory;
                } else {
                    if (selectedClassName.equals(factory.getClass().getName())) {
                        effectiveFactory = factory;
                        break;
                    }
                }
            }
        }

        if (effectiveFactory == null) {
            if ((selectedClassName == null) || factories.isEmpty()) {
                throw new IllegalStateException("No HTTP client has been found in the classpath");
            } else {
                throw new IllegalStateException(String.format(
                        "The value of the `langchain4j.http.clientBuilderFactory` system property does not match any"
                                + " of the available HTTP Clients in the classpath: %s.",
                        factoryNames(factories)));
            }
        }

        return effectiveFactory.create();
    }

    private static Collection<HttpClientBuilderFactory> loadFactories() {
        // ServiceLoader validates that its caller module declares `uses HttpClientBuilderFactory`.
        // Keep these calls in this module instead of delegating them to a generic helper in langchain4j-core.
        List<HttpClientBuilderFactory> factories = loadAll(ServiceLoader.load(HttpClientBuilderFactory.class));
        if (factories.isEmpty()) {
            factories = loadAll(
                    ServiceLoader.load(HttpClientBuilderFactory.class, HttpClientBuilderLoader.class.getClassLoader()));
        }
        return factories;
    }

    private static List<HttpClientBuilderFactory> loadAll(ServiceLoader<HttpClientBuilderFactory> loader) {
        List<HttpClientBuilderFactory> factories = new ArrayList<>();
        loader.iterator().forEachRemaining(factories::add);
        return factories;
    }

    private static Set<String> factoryNames(Collection<HttpClientBuilderFactory> factories) {
        return factories.stream().map(f -> f.getClass().getName()).collect(Collectors.toSet());
    }
}
