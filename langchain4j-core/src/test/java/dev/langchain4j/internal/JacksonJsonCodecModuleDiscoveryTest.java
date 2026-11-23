package dev.langchain4j.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.Enumeration;
import org.junit.jupiter.api.Test;

class JacksonJsonCodecModuleDiscoveryTest {

    record SamplePerson(String name, int age) {}

    @Test
    void should_serialize_and_deserialize_dates() {
        JacksonJsonCodec codec = new JacksonJsonCodec();

        LocalDate date = LocalDate.of(2026, 11, 2);
        String dateJson = codec.toJson(date);
        assertThat(dateJson).isEqualTo("\"2026-11-02\"");
        assertThat(codec.fromJson(dateJson, LocalDate.class)).isEqualTo(date);

        LocalTime time = LocalTime.of(14, 30, 15);
        String timeJson = codec.toJson(time);
        assertThat(timeJson).isEqualTo("\"14:30:15\"");
        assertThat(codec.fromJson(timeJson, LocalTime.class)).isEqualTo(time);

        LocalDateTime dateTime = LocalDateTime.of(2026, 11, 2, 14, 30, 15);
        String dateTimeJson = codec.toJson(dateTime);
        assertThat(dateTimeJson).isEqualTo("\"2026-11-02T14:30:15\"");
        assertThat(codec.fromJson(dateTimeJson, LocalDateTime.class)).isEqualTo(dateTime);
    }

    @Test
    void should_serialize_and_deserialize_objects() {
        JacksonJsonCodec codec = new JacksonJsonCodec();

        SamplePerson person = new SamplePerson("Alice", 30);
        String json = codec.toJson(person);
        assertThat(json).contains("\"name\":\"Alice\"").contains("\"age\":30");

        SamplePerson deserialized = codec.fromJson(json, SamplePerson.class);
        assertThat(deserialized).isEqualTo(person);
    }

    @Test
    void should_gracefully_handle_service_configuration_error_when_discovering_modules() throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            // A custom URL returning a provider class name that does not exist,
            // which causes ServiceLoader to throw java.util.ServiceConfigurationError.
            URL failingServiceUrl = new URL(
                    "memory",
                    "",
                    -1,
                    "/META-INF/services/com.fasterxml.jackson.databind.Module",
                    new URLStreamHandler() {
                        @Override
                        protected URLConnection openConnection(URL u) {
                            return new URLConnection(u) {
                                @Override
                                public void connect() {}

                                @Override
                                public InputStream getInputStream() {
                                    return new ByteArrayInputStream(
                                            "com.example.NonExistentJacksonModule\n".getBytes(StandardCharsets.UTF_8));
                                }
                            };
                        }
                    });

            ClassLoader brokenClassLoader = new ClassLoader(originalClassLoader) {
                @Override
                public Enumeration<URL> getResources(String name) throws IOException {
                    if ("META-INF/services/com.fasterxml.jackson.databind.Module".equals(name)) {
                        return Collections.enumeration(Collections.singletonList(failingServiceUrl));
                    }
                    return super.getResources(name);
                }
            };

            Thread.currentThread().setContextClassLoader(brokenClassLoader);

            assertThatCode(() -> {
                        JacksonJsonCodec codec = new JacksonJsonCodec();
                        LocalDate date = LocalDate.of(2026, 11, 2);
                        String json = codec.toJson(date);
                        assertThat(json).isEqualTo("\"2026-11-02\"");
                        assertThat(codec.fromJson(json, LocalDate.class)).isEqualTo(date);
                        assertThat(codec.getObjectMapper().getRegisteredModuleIds())
                                .contains("langchain4j-module");
                    })
                    .doesNotThrowAnyException();
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void should_propagate_fatal_errors_such_as_out_of_memory_error() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            ClassLoader oomThrowingClassLoader = new ClassLoader(originalClassLoader) {
                @Override
                public Enumeration<URL> getResources(String name) {
                    if ("META-INF/services/com.fasterxml.jackson.databind.Module".equals(name)) {
                        throw new OutOfMemoryError("Simulated OOM during module discovery");
                    }
                    try {
                        return super.getResources(name);
                    } catch (IOException e) {
                        return Collections.emptyEnumeration();
                    }
                }
            };

            Thread.currentThread().setContextClassLoader(oomThrowingClassLoader);

            assertThatThrownBy(JacksonJsonCodec::new)
                    .isInstanceOf(OutOfMemoryError.class)
                    .hasMessage("Simulated OOM during module discovery");
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }
}
