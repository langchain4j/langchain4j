package dev.langchain4j.json.jackson3;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.ProviderJson;
import dev.langchain4j.internal.ProviderJsonSpec;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionRequest;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * A builder-based DTO is deserialized differently by the two Jackson versions. Jackson 2 follows
 * {@code @JsonDeserialize(builder = ...)} and finishes by calling {@code build()}. Jackson 3 cannot
 * see that annotation - it lives in Jackson 2's databind package - and instead uses the
 * {@code @JsonCreator} constructor that takes the builder, populating the builder's fields and
 * handing it over without ever calling {@code build()}.
 *
 * <p>So anything {@code build()} does beyond {@code new X(this)} silently stops happening. That is
 * how {@code langchain4j-mistral-ai} came to drop every tool call: the type discriminator was
 * defaulted inside {@code build()}, and under Jackson 3 it arrived null.
 *
 * <p>This checks the OpenAI DTOs for that, by comparing an instance built through the builder with
 * one parsed from an empty JSON object. Both should be a DTO with nothing set; they differ exactly
 * when {@code build()} contributes something the creator route skips.
 *
 * <p>OpenAI is checked from here rather than from {@code langchain4j-open-ai} itself because that
 * module cannot depend on this one - {@code langchain4j-json-jackson3} needs {@code langchain4j},
 * whose tests need {@code langchain4j-open-ai}, so the {@code jackson3} profile would make the
 * module graph cyclic. Every other migrated module runs its own suite under that profile instead.
 */
class OpenAiBuilderCreatorParityTest {

    private static final String PACKAGE = "dev/langchain4j/model/openai/internal";

    /**
     * OpenAI holds the largest set of these DTOs in the repository. If scanning silently found
     * far fewer than are there, this test would pass without checking anything.
     */
    private static final int EXPECTED_AT_LEAST = 40;

    private final Json.JsonCodec codec = ProviderJson.codec(ProviderJsonSpec.builder()
            .propertyNaming(ProviderJsonSpec.PropertyNaming.SNAKE_CASE)
            .build());

    @TestFactory
    List<DynamicTest> build_contributes_nothing_the_json_creator_would_skip() throws Exception {
        List<Class<?>> dtos = builderBackedDtos();

        assertThat(dtos)
                .as("DTOs found under %s - scanning is broken if this is empty or short", PACKAGE)
                .hasSizeGreaterThanOrEqualTo(EXPECTED_AT_LEAST);

        List<DynamicTest> tests = new ArrayList<>();
        for (Class<?> dto : dtos) {
            tests.add(dynamicTest(dto.getSimpleName(), () -> {
                Object viaBuilder = buildEmpty(dto);
                Object viaCreator = codec.fromJson("{}", dto);

                assertThat(viaCreator)
                        .usingRecursiveComparison()
                        .as(
                                "%s.build() sets something the @JsonCreator route does not, so it is "
                                        + "lost under Jackson 3. Move the default onto the builder field.",
                                dto.getSimpleName())
                        .isEqualTo(viaBuilder);
            }));
        }
        return tests;
    }

    /**
     * The check above is only worth having if it fails when {@code build()} does contribute
     * something. This is the shape that dropped every mistral-ai tool call.
     */
    @Test
    void a_default_applied_inside_build_does_not_survive_the_creator_route() {
        DefaultedInBuild viaBuilder = DefaultedInBuild.builder().build();
        DefaultedInBuild viaCreator = codec.fromJson("{}", DefaultedInBuild.class);

        assertThat(viaBuilder.type).isEqualTo("function");
        assertThat(viaCreator.type).isNull();

        assertThatThrownBy(() -> assertThat(viaCreator).usingRecursiveComparison().isEqualTo(viaBuilder))
                .isInstanceOf(AssertionError.class);
    }

    static class DefaultedInBuild {

        final String type;

        @JsonCreator
        DefaultedInBuild(Builder builder) {
            this.type = builder.type;
        }

        static Builder builder() {
            return new Builder();
        }

        @JsonAutoDetect(fieldVisibility = ANY)
        static class Builder {

            String type;

            DefaultedInBuild build() {
                if (type == null) {
                    type = "function";
                }
                return new DefaultedInBuild(this);
            }
        }
    }

    private static Object buildEmpty(Class<?> dto) throws Exception {
        Object builder = dto.getMethod("builder").invoke(null);
        return builder.getClass().getMethod("build").invoke(builder);
    }

    /**
     * Classes with a static {@code builder()} and a {@code @JsonCreator} constructor taking that
     * builder. A DTO whose builder validates required fields cannot be built empty, so it is left
     * out rather than reported as a failure.
     */
    private static List<Class<?>> builderBackedDtos() throws Exception {
        List<Class<?>> dtos = new ArrayList<>();
        for (String className : classNamesIn(PACKAGE)) {
            Class<?> type;
            try {
                type = Class.forName(className);
            } catch (Throwable e) {
                continue;
            }
            if (type.isInterface() || type.isEnum() || Modifier.isAbstract(type.getModifiers())) {
                continue;
            }
            Method builder;
            try {
                builder = type.getMethod("builder");
            } catch (NoSuchMethodException e) {
                continue;
            }
            if (!Modifier.isStatic(builder.getModifiers()) || !takesItsBuilder(type, builder.getReturnType())) {
                continue;
            }
            try {
                buildEmpty(type);
            } catch (Exception e) {
                continue;
            }
            dtos.add(type);
        }
        dtos.sort(java.util.Comparator.comparing(Class::getName));
        return dtos;
    }

    private static boolean takesItsBuilder(Class<?> type, Class<?> builderType) {
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            boolean isCreator = constructor.isAnnotationPresent(com.fasterxml.jackson.annotation.JsonCreator.class);
            if (isCreator
                    && constructor.getParameterCount() == 1
                    && constructor.getParameterTypes()[0].isAssignableFrom(builderType)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> classNamesIn(String packagePath) throws Exception {
        Path location = Path.of(ChatCompletionRequest.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());

        List<String> entries = new ArrayList<>();
        if (Files.isDirectory(location)) {
            try (Stream<Path> files = Files.walk(location.resolve(packagePath))) {
                files.filter(p -> p.toString().endsWith(".class"))
                        .forEach(p -> entries.add(
                                location.relativize(p).toString().replace(java.io.File.separatorChar, '/')));
            }
        } else {
            try (JarFile jar = new JarFile(location.toFile())) {
                jar.stream()
                        .map(JarEntry::getName)
                        .filter(name -> name.startsWith(packagePath) && name.endsWith(".class"))
                        .forEach(entries::add);
            }
        }

        return entries.stream()
                .map(name -> name.substring(0, name.length() - ".class".length()).replace('/', '.'))
                .toList();
    }
}
