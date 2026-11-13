package dev.langchain4j.http.client;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    void should_select_highest_priority_independently_of_discovery_order() {
        HttpClientBuilderFactory fallback = factory(-1);
        HttpClientBuilderFactory preferred = factory(0);
        HttpClientBuilder expected = preferred.create();
        for (var factories : List.of(List.of(fallback, preferred), List.of(preferred, fallback))) {
            try (MockedStatic<ServiceHelper> mocked = Mockito.mockStatic(ServiceHelper.class)) {
                mocked.when(() -> ServiceHelper.loadFactories(HttpClientBuilderFactory.class))
                        .thenReturn(factories);
                assertThat(HttpClientBuilderLoader.loadHttpClientBuilder()).isSameAs(expected);
            }
        }
        verify(fallback, never()).create();
    }

    @Test
    void should_ignore_ties_below_the_highest_priority() {
        HttpClientBuilderFactory first = factory(0);
        HttpClientBuilderFactory second = factory(0);
        HttpClientBuilderFactory preferred = factory(1);
        HttpClientBuilder expected = preferred.create();
        for (var factories : List.of(
                List.of(first, second, preferred),
                List.of(first, preferred, second),
                List.of(preferred, first, second))) {
            try (MockedStatic<ServiceHelper> mocked = Mockito.mockStatic(ServiceHelper.class)) {
                mocked.when(() -> ServiceHelper.loadFactories(HttpClientBuilderFactory.class))
                        .thenReturn(factories);
                assertThat(HttpClientBuilderLoader.loadHttpClientBuilder()).isSameAs(expected);
            }
        }
        verify(first, never()).create();
        verify(second, never()).create();
    }

    @Test
    void should_reject_tied_highest_priorities() {
        var factories = List.of(factory(-1), factory(2), factory(2));
        try (MockedStatic<ServiceHelper> mocked = Mockito.mockStatic(ServiceHelper.class)) {
            mocked.when(() -> ServiceHelper.loadFactories(HttpClientBuilderFactory.class))
                    .thenReturn(factories);
            assertThatThrownBy(HttpClientBuilderLoader::loadHttpClientBuilder)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("multiple HTTP clients");
        }
    }

    @Test
    void should_select_single_factory_with_minimum_priority() {
        HttpClientBuilderFactory factory = factory(Integer.MIN_VALUE);
        HttpClientBuilder expected = factory.create();
        try (MockedStatic<ServiceHelper> mocked = Mockito.mockStatic(ServiceHelper.class)) {
            mocked.when(() -> ServiceHelper.loadFactories(HttpClientBuilderFactory.class))
                    .thenReturn(List.of(factory));
            assertThat(HttpClientBuilderLoader.loadHttpClientBuilder()).isSameAs(expected);
        }
    }

    @Test
    void property_should_override_priority() {
        HttpClientBuilderFactory selected = MockHttpClientBuilder.MockClientFactory.of();
        var factories = List.of(factory(10), selected, factory(10));
        try (var ignored = ResettableSystemProperties.of(
                        "langchain4j.http.clientBuilderFactory",
                        selected.getClass().getName());
                MockedStatic<ServiceHelper> mocked = Mockito.mockStatic(ServiceHelper.class)) {
            mocked.when(() -> ServiceHelper.loadFactories(HttpClientBuilderFactory.class))
                    .thenReturn(factories);
            assertThat(HttpClientBuilderLoader.loadHttpClientBuilder()).isInstanceOf(MockHttpClientBuilder.class);
        }
    }

    private static HttpClientBuilderFactory factory(int priority) {
        HttpClientBuilderFactory factory = mock(HttpClientBuilderFactory.class);
        when(factory.priority()).thenReturn(priority);
        when(factory.create()).thenReturn(mock(HttpClientBuilder.class));
        return factory;
    }

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
