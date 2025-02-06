package dev.langchain4j.http.client;

import dev.langchain4j.Experimental;

import java.util.Collection;
import java.util.List;

import static dev.langchain4j.spi.ServiceHelper.loadFactories;

@Experimental
public class HttpClientBuilderLoader {

    public static HttpClientBuilder loadHttpClientBuilder() {
        Collection<HttpClientBuilderFactory> factories = loadFactories(HttpClientBuilderFactory.class);

        if (factories.size() > 1) {
            List<String> factoryNames = factories.stream()
                    .map(factory -> factory.getClass().getName())
                    .toList();
            throw new IllegalStateException(String.format("Conflict: multiple HTTP clients have been found " +
                    "in the classpath: %s. Please explicitly specify the one you wish to use.", factoryNames));
        }

        for (HttpClientBuilderFactory factory : factories) {
            return factory.create();
        }

        throw new IllegalStateException("No HTTP client has been found in the classpath");
    }
}
