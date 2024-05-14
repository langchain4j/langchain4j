package dev.langchain4j.http;

import dev.langchain4j.Experimental;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;

import static dev.langchain4j.spi.ServiceHelper.loadFactories;

@Slf4j
@Experimental
public class HttpClientBuilderLoader {

    public static HttpClientBuilder loadHttpClientBuilder() {
        Collection<HttpClientBuilderFactory> factories = loadFactories(HttpClientBuilderFactory.class);
        if (factories.size() > 1) {
            // TODO log names of factories
            throw new RuntimeException("Conflict: multiple HTTP clients have been found in the classpath. " +
                    "Please explicitly specify the one you wish to use.");
        }

        for (HttpClientBuilderFactory factory : factories) {
            HttpClientBuilder httpClientBuilder = factory.create();
            log.debug("Loaded the following HTTP client through SPI: {}", httpClientBuilder);
            return httpClientBuilder;
        }

        return null;
    }
}
