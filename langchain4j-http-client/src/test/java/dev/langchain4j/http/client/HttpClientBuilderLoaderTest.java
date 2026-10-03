package dev.langchain4j.http.client;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import dev.langchain4j.Internal;
import dev.langchain4j.spi.ServiceHelper;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HttpClientBuilderLoaderTest {

    @Test
    void loadsFactoryFromNamedModule(@TempDir Path tempDir) throws Exception {
        Path coreModule = Files.createDirectory(tempDir.resolve("langchain4j-core"));
        // Include ServiceHelper so this test fails if service loading is delegated back to langchain4j-core.
        copyClass(Internal.class, coreModule);
        copyClass(ServiceHelper.class, coreModule);
        Path coreModuleInfo = writeSource(coreModule, "module-info.java", """
            module langchain4j.core {
                exports dev.langchain4j.spi;
            }
            """);
        compile(
                "-d",
                coreModule.toString(),
                "--patch-module",
                "langchain4j.core=" + coreModule,
                coreModuleInfo.toString());

        Path httpClientModule = Files.createDirectory(tempDir.resolve("langchain4j-http-client"));
        copyClass(HttpClient.class, httpClientModule);
        copyClass(HttpClientBuilder.class, httpClientModule);
        copyClass(HttpClientBuilderFactory.class, httpClientModule);
        copyClass(HttpClientBuilderLoader.class, httpClientModule);
        Path httpClientModuleInfo = writeSource(httpClientModule, "module-info.java", """
            module langchain4j.http.client {
                requires langchain4j.core;
                exports dev.langchain4j.http.client;
                uses dev.langchain4j.http.client.HttpClientBuilderFactory;
            }
            """);
        compile(
                "-d",
                httpClientModule.toString(),
                "--module-path",
                coreModule.toString(),
                "--patch-module",
                "langchain4j.http.client=" + httpClientModule,
                httpClientModuleInfo.toString());

        Path providerSources = Files.createDirectory(tempDir.resolve("provider-sources"));
        Path providerModuleInfo = writeSource(providerSources, "module-info.java", """
            module test.http.client.provider {
                requires langchain4j.http.client;
                provides dev.langchain4j.http.client.HttpClientBuilderFactory
                        with test.http.client.provider.TestHttpClientBuilderFactory;
            }
            """);
        Path providerSource =
                writeSource(providerSources, "test/http/client/provider/TestHttpClientBuilderFactory.java", """
                    package test.http.client.provider;

                    import dev.langchain4j.http.client.HttpClient;
                    import dev.langchain4j.http.client.HttpClientBuilder;
                    import dev.langchain4j.http.client.HttpClientBuilderFactory;
                    import java.time.Duration;

                    public class TestHttpClientBuilderFactory implements HttpClientBuilderFactory {
                        @Override
                        public HttpClientBuilder create() {
                            return new TestHttpClientBuilder();
                        }

                        public static class TestHttpClientBuilder implements HttpClientBuilder {
                            @Override
                            public Duration connectTimeout() {
                                return null;
                            }

                            @Override
                            public HttpClientBuilder connectTimeout(Duration timeout) {
                                return this;
                            }

                            @Override
                            public Duration readTimeout() {
                                return null;
                            }

                            @Override
                            public HttpClientBuilder readTimeout(Duration timeout) {
                                return this;
                            }

                            @Override
                            public HttpClient build() {
                                return null;
                            }
                        }
                    }
                    """);
        Path providerModule = Files.createDirectory(tempDir.resolve("provider"));
        compile(
                "-d",
                providerModule.toString(),
                "--module-path",
                modulePath(coreModule, httpClientModule),
                providerModuleInfo.toString(),
                providerSource.toString());

        ModuleFinder finder = ModuleFinder.of(coreModule, httpClientModule, providerModule);
        Configuration configuration = ModuleLayer.boot()
                .configuration()
                .resolveAndBind(finder, ModuleFinder.of(), Set.of("langchain4j.http.client"));
        ModuleLayer layer =
                ModuleLayer.boot().defineModulesWithOneLoader(configuration, ClassLoader.getPlatformClassLoader());
        Class<?> loaderClass =
                layer.findLoader("langchain4j.http.client").loadClass(HttpClientBuilderLoader.class.getName());
        Method loadHttpClientBuilder = loaderClass.getMethod("loadHttpClientBuilder");

        assertThat(loadHttpClientBuilder.invoke(null).getClass().getName())
                .isEqualTo("test.http.client.provider.TestHttpClientBuilderFactory$TestHttpClientBuilder");
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
        assertThatThrownBy(() -> HttpClientBuilderLoader.loadHttpClientBuilder(Collections.emptyList()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No HTTP client");
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
        assertThat(HttpClientBuilderLoader.loadHttpClientBuilder(List.of(MockHttpClientBuilder.MockClientFactory.of())))
                .isInstanceOf(MockHttpClientBuilder.class);
    }

    @Test
    void singleFactoryNonMatchingProperty() {
        try (var ignored = ResettableSystemProperties.of("langchain4j.http.clientBuilderFactory", "whatever")) {
            assertThatThrownBy(() -> HttpClientBuilderLoader.loadHttpClientBuilder(
                            List.of(MockHttpClientBuilder.MockClientFactory.of())))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("does not match any of the available HTTP Clients");
        }
    }

    @Test
    void multipleFactories() {
        assertThatThrownBy(() -> HttpClientBuilderLoader.loadHttpClientBuilder(
                        List.of(MockHttpClientBuilder.MockClientFactory.of(), TestHttpClientBuilderFactory.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("multiple HTTP clients");
    }

    @Test
    void multipleFactoriesNonMatchingProperty() {
        try (var ignored = ResettableSystemProperties.of("langchain4j.http.clientBuilderFactory", "whatever")) {
            assertThatThrownBy(() -> HttpClientBuilderLoader.loadHttpClientBuilder(
                            List.of(MockHttpClientBuilder.MockClientFactory.of(), TestHttpClientBuilderFactory.of())))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("does not match any of the available HTTP Clients");
        }
    }

    @Test
    void multipleFactoriesMatchingProperty() {
        try (var ignored = ResettableSystemProperties.of(
                "langchain4j.http.clientBuilderFactory", MockHttpClientBuilder.MockClientFactory.class.getName())) {
            assertThat(HttpClientBuilderLoader.loadHttpClientBuilder(List.of(
                            TestHttpClientBuilderFactory.of(),
                            MockHttpClientBuilder.MockClientFactory.of(),
                            TestHttpClientBuilderFactory.of())))
                    .isInstanceOf(MockHttpClientBuilder.class);
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

    private static void copyClass(Class<?> type, Path moduleDirectory) throws IOException {
        String resourceName = type.getName().replace('.', '/') + ".class";
        Path target = moduleDirectory.resolve(resourceName);
        Files.createDirectories(target.getParent());
        try (InputStream source = type.getClassLoader().getResourceAsStream(resourceName)) {
            Files.copy(Objects.requireNonNull(source), target);
        }
    }

    private static Path writeSource(Path directory, String relativePath, String source) throws IOException {
        Path path = directory.resolve(relativePath);
        Files.createDirectories(path.getParent());
        return Files.writeString(path, source);
    }

    private static void compile(String... arguments) {
        int exitCode = ToolProvider.getSystemJavaCompiler().run(null, null, null, arguments);
        assertThat(exitCode).isZero();
    }

    private static String modulePath(Path... modules) {
        return String.join(
                File.pathSeparator,
                java.util.Arrays.stream(modules).map(Path::toString).toList());
    }
}
