package dev.langchain4j.service.output;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.ImageContent;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ServiceOutputParserImageReturnTypeTest {

    private final ServiceOutputParser parser = new ServiceOutputParser();

    @ParameterizedTest
    @MethodSource("imageReturnTypes")
    void should_not_request_json_schema_for_image_return_types(Type returnType) {
        assertThat(parser.jsonSchema(returnType)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("imageReturnTypes")
    void should_not_request_text_format_for_image_return_types(Type returnType) {
        assertThat(parser.outputFormatInstructions(returnType)).isEmpty();
    }

    static Stream<Type> imageReturnTypes() {
        return Stream.of(
                Image.class,
                ImageContent.class,
                new TypeReference<List<Image>>() {}.getType(),
                new TypeReference<Set<Image>>() {}.getType(),
                new TypeReference<Collection<Image>>() {}.getType(),
                new TypeReference<List<ImageContent>>() {}.getType(),
                new TypeReference<Set<ImageContent>>() {}.getType(),
                new TypeReference<Collection<ImageContent>>() {}.getType());
    }

    @ParameterizedTest
    @MethodSource("structuredReturnTypes")
    void should_keep_schema_for_textual_return_types(Type returnType) {
        assertThat(parser.jsonSchema(returnType)).isPresent();
    }

    @Test
    void should_keep_text_format_for_pojo_return_types() {
        assertThat(parser.outputFormatInstructions(Answer.class)).isNotEmpty();
    }

    static Stream<Type> structuredReturnTypes() {
        return Stream.of(
                Answer.class,
                new TypeReference<List<Answer>>() {}.getType(),
                new TypeReference<Set<Answer>>() {}.getType(),
                new TypeReference<List<String>>() {}.getType());
    }

    record Answer(String text) {}
}
