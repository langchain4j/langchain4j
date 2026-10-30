package dev.langchain4j.http.client;

import dev.langchain4j.spi.PrioritizedFactory;

public interface HttpClientBuilderFactory extends PrioritizedFactory {

    HttpClientBuilder create();

    /**
     * The priority used when selecting a factory from the classpath. Higher values take precedence.
     * Factories with the same highest priority cause a conflict. An explicit
     * {@code langchain4j.http.clientBuilderFactory} system property overrides this priority.
     *
     * @return the priority, {@link #DEFAULT_PRIORITY} by default; the JDK implementation uses
     *         {@link #YIELDS_TO_OTHERS} as a fallback
     * @since 1.23.0
     */
    @Override
    default int priority() {
        return DEFAULT_PRIORITY;
    }
}
