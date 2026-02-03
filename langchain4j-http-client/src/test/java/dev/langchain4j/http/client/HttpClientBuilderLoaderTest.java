package dev.langchain4j.http.client;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import dev.langchain4j.spi.ServiceHelper;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class HttpClientBuilderLoaderTest {

    @Test
    void noFactories() {
        doNoFactories();
    }

    @Test
    void noFactoriesAndProperty() {
        try (var ignored = ResettableSystemProperties.of("langchain4j.http.clientBuilderFactory", "whatever")) {
            doNoFactories();
        }
    }

    private void doNoFactories() {
        try (MockedStatic<ServiceHelper> mocked = Mockito.mockStatic(ServiceHelper.class)) {
            mocked.when(() -> ServiceHelper.loadFactories(HttpClientBuilderFactory.class))
                    .thenReturn(Collections.emptyList());

            assertThatThrownBy(HttpClientBuilderLoader::loadHttpClientBuilder)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No HTTP client");
        }
    }

    @Test
    void singleFactory() {
        doSingleFactory();
    }

    @Test
    void singleFactoryMatchingProperty() {
        try (var ignored = ResettableSystemProperties.of(
                "langchain4j.http.clientBuilderFactory", MockHttpClientBuilder.MockClientFactory.class.getName())) {
            doSingleFactory();
        }
    }

    private void doSingleFactory() {
        try (MockedStatic<ServiceHelper> mocked = Mockito.mockStatic(ServiceHelper.class)) {
            mocked.when(() -> ServiceHelper.loadFactories(HttpClientBuilderFactory.class))
                    .thenReturn(List.of(MockHttpClientBuilder.MockClientFactory.of()));

            assertThat(HttpClientBuilderLoader.loadHttpClientBuilder()).isInstanceOf(MockHttpClientBuilder.class);
        }
    }

    @Test
    void singleFactoryNonMatchingProperty() {
        try (var ignored = ResettableSystemProperties.of("langchain4j.http.clientBuilderFactory", "whatever")) {
            try (MockedStatic<ServiceHelper> mocked = Mockito.mockStatic(ServiceHelper.class)) {
                mocked.when(() -> ServiceHelper.loadFactories(HttpClientBuilderFactory.class))
                        .thenReturn(List.of(MockHttpClientBuilder.MockClientFactory.of()));

                assertThatThrownBy(HttpClientBuilderLoader::loadHttpClientBuilder)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("does not match any of the available HTTP Clients");
            }
        }
    }

    @Test
    void multipleFactories() {
        try (MockedStatic<ServiceHelper> mocked = Mockito.mockStatic(ServiceHelper.class)) {
            mocked.when(() -> ServiceHelper.loadFactories(HttpClientBuilderFactory.class))
                    .thenReturn(
                            List.of(MockHttpClientBuilder.MockClientFactory.of(), TestHttpClientBuilderFactory.of()));

            assertThatThrownBy(HttpClientBuilderLoader::loadHttpClientBuilder)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("multiple HTTP clients");
        }
    }

    @Test
    void multipleFactoriesNonMatchingProperty() {
        try (var ignored = ResettableSystemProperties.of("langchain4j.http.clientBuilderFactory", "whatever")) {
            try (MockedStatic<ServiceHelper> mocked = Mockito.mockStatic(ServiceHelper.class)) {
                mocked.when(() -> ServiceHelper.loadFactories(HttpClientBuilderFactory.class))
                        .thenReturn(List.of(
                                MockHttpClientBuilder.MockClientFactory.of(), TestHttpClientBuilderFactory.of()));

                assertThatThrownBy(HttpClientBuilderLoader::loadHttpClientBuilder)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("does not match any of the available HTTP Clients");
            }
        }
    }

    @Test
    void multipleFactoriesMatchingProperty() {
        try (var ignored = ResettableSystemProperties.of(
                "langchain4j.http.clientBuilderFactory", MockHttpClientBuilder.MockClientFactory.class.getName())) {
            try (MockedStatic<ServiceHelper> mocked = Mockito.mockStatic(ServiceHelper.class)) {
                mocked.when(() -> ServiceHelper.loadFactories(HttpClientBuilderFactory.class))
                        .thenReturn(List.of(
                                TestHttpClientBuilderFactory.of(),
                                MockHttpClientBuilder.MockClientFactory.of(),
                                TestHttpClientBuilderFactory.of()));

                assertThat(HttpClientBuilderLoader.loadHttpClientBuilder()).isInstanceOf(MockHttpClientBuilder.class);
            }
        }
    }

    // Copied from `io.quarkus.runtime.ResettableSystemProperties`. Might be useful to make this accessible
    private static class ResettableSystemProperties implements AutoCloseable {

        private final Map<String, String> toRestore;

        public ResettableSystemProperties(Map<String, String> toSet) {
            Objects.requireNonNull(toSet);
            if (toSet.isEmpty()) {
                toRestore = Collections.emptyMap();
                return;
            }
            toRestore = new HashMap<>();
            for (var entry : toSet.entrySet()) {
                String oldValue = System.setProperty(entry.getKey(), entry.getValue());
                toRestore.put(entry.getKey(), oldValue);
            }
        }

        public static ResettableSystemProperties of(String name, String value) {
            return new ResettableSystemProperties(Map.of(name, value));
        }

        public static ResettableSystemProperties empty() {
            return new ResettableSystemProperties(Collections.emptyMap());
        }

        @Override
        public void close() {
            for (var entry : toRestore.entrySet()) {
                if (entry.getValue() != null) {
                    System.setProperty(entry.getKey(), entry.getValue());
                } else {
                    System.clearProperty(entry.getKey());
                }
            }
        }
    }

    private static class TestHttpClientBuilderFactory implements HttpClientBuilderFactory {

        public static TestHttpClientBuilderFactory of() {
            return new TestHttpClientBuilderFactory();
        }

        @Override
        public HttpClientBuilder create() {
            throw new IllegalStateException("should never be called");
        }
    }
}
