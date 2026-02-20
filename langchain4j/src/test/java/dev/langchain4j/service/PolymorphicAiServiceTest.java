package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import java.util.List;
import org.junit.jupiter.api.Test;

class PolymorphicAiServiceTest {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", visible = true)
    @JsonSubTypes({
        @JsonSubTypes.Type(value = TextResponse.class, name = "text"),
        @JsonSubTypes.Type(value = ImageResponse.class, name = "image")
    })
    interface ChatbotResponse {}

    @JsonTypeName("text")
    record TextResponse(String type, String text) implements ChatbotResponse {}

    @JsonTypeName("image")
    record ImageResponse(String type, String url) implements ChatbotResponse {}

    interface ChatbotService {
        ChatbotResponse reply(String userMessage);
    }

    interface ChatbotListService {
        List<ChatbotResponse> reply(String userMessage);
    }

    interface PolymorphicResponder {
        @UserMessage("Respond to user message: {{it}}")
        ChatbotResponse respond(String userMessage);
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind", visible = true)
    @JsonSubTypes({@JsonSubTypes.Type(value = SummaryResponse.class, name = "summary")})
    interface KindBasedResponse {}

    record SummaryResponse(String kind, String summary) implements KindBasedResponse {}

    interface KindBasedService {
        KindBasedResponse reply(String userMessage);
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", visible = true)
    @JsonSubTypes({@JsonSubTypes.Type(value = ExplicitOnlyText.class, name = "explicit-text")})
    interface ExplicitOnlyResponse {}

    record ExplicitOnlyText(String type, String text) implements ExplicitOnlyResponse {}

    interface ExplicitOnlyService {
        ExplicitOnlyResponse reply(String userMessage);
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", visible = true)
    @JsonSubTypes({
        @JsonSubTypes.Type(value = DuplicateTextA.class, name = "dup"),
        @JsonSubTypes.Type(value = DuplicateTextB.class, name = "dup")
    })
    interface DuplicateNameResponse {}

    record DuplicateTextA(String type, String text) implements DuplicateNameResponse {}

    record DuplicateTextB(String type, String text) implements DuplicateNameResponse {}

    interface DuplicateNameService {
        DuplicateNameResponse reply(String userMessage);
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
    @JsonSubTypes({@JsonSubTypes.Type(MinimalOnly.class)})
    interface MinimalSubTypeResponse {}

    record MinimalOnly(String type, String text) implements MinimalSubTypeResponse {}

    interface MinimalSubTypeService {
        MinimalSubTypeResponse reply(String userMessage);
    }

    @Test
    void should_deserialize_text_response() {
        String json = """
            { "type": "text", "text": "hello" }
        """;

        ChatModelMock model = ChatModelMock.thatAlwaysResponds(json);

        ChatbotService service =
                AiServices.builder(ChatbotService.class).chatModel(model).build();

        ChatbotResponse response = service.reply("hi");

        assertThat(response).isInstanceOf(TextResponse.class);
        TextResponse text = (TextResponse) response;
        assertThat(text.type()).isEqualTo("text");
        assertThat(text.text()).isEqualTo("hello");
    }

    @Test
    void should_deserialize_image_response() {
        String json = """
            { "type": "image", "url": "https://example.com/x.png" }
        """;

        ChatModelMock model = ChatModelMock.thatAlwaysResponds(json);

        ChatbotService service =
                AiServices.builder(ChatbotService.class).chatModel(model).build();

        ChatbotResponse response = service.reply("show image");

        assertThat(response).isInstanceOf(ImageResponse.class);
        ImageResponse image = (ImageResponse) response;
        assertThat(image.type()).isEqualTo("image");
        assertThat(image.url()).isEqualTo("https://example.com/x.png");
    }

    @Test
    void should_fail_on_unknown_discriminator() {
        String json = """
            { "type": "unknown", "text": "x" }
        """;

        ChatModelMock model = ChatModelMock.thatAlwaysResponds(json);

        ChatbotService service =
                AiServices.builder(ChatbotService.class).chatModel(model).build();

        assertThatThrownBy(() -> service.reply("hi"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Unknown discriminator value");
    }

    @Test
    void prompt_should_contain_discriminator_instructions() {
        ChatModelMock model = ChatModelMock.thatAlwaysResponds(
                """
                { "type": "text", "text": "ok" }
                """);

        ChatbotService service =
                AiServices.builder(ChatbotService.class).chatModel(model).build();

        service.reply("hello");

        String prompt = model.userMessageText();
        assertThat(prompt)
                .contains("discriminator 'type'")
                .contains("type=text")
                .contains("type=image");
    }

    @Test
    void should_handle_polymorphic_return_type_with_user_message_template() {
        ChatModelMock model = ChatModelMock.thatAlwaysResponds(
                """
                { "type": "image", "url": "https://example.com/cat.png" }
                """);

        PolymorphicResponder responder = AiServices.create(PolymorphicResponder.class, model);

        ChatbotResponse response = responder.respond("show me a cat image");

        assertThat(response).isInstanceOf(ImageResponse.class);
        assertThat(((ImageResponse) response).url()).startsWith("https://example.com/cat");

        String prompt = model.userMessageText();
        assertThat(prompt)
                .contains("Respond to user message: show me a cat image")
                .contains("discriminator 'type'")
                .contains("type=text")
                .contains("type=image");
    }

    @Test
    void should_use_explicit_subtype_names_from_json_sub_types() {
        String json = """
            { "type": "explicit-text", "text": "hello" }
        """;

        ChatModelMock model = ChatModelMock.thatAlwaysResponds(json);

        ExplicitOnlyService service =
                AiServices.builder(ExplicitOnlyService.class).chatModel(model).build();

        ExplicitOnlyResponse response = service.reply("hi");

        assertThat(response).isInstanceOf(ExplicitOnlyText.class);
        ExplicitOnlyText text = (ExplicitOnlyText) response;
        assertThat(text.type()).isEqualTo("explicit-text");
        assertThat(text.text()).isEqualTo("hello");
    }

    @Test
    void should_respect_custom_discriminator_property() {
        String json = """
            { "kind": "summary", "summary": "ok" }
        """;

        ChatModelMock model = ChatModelMock.thatAlwaysResponds(json);

        KindBasedService service =
                AiServices.builder(KindBasedService.class).chatModel(model).build();

        KindBasedResponse response = service.reply("summarize");

        assertThat(response).isInstanceOf(SummaryResponse.class);
        SummaryResponse summary = (SummaryResponse) response;
        assertThat(summary.kind()).isEqualTo("summary");
        assertThat(summary.summary()).isEqualTo("ok");

        String prompt = model.userMessageText();
        assertThat(prompt).contains("discriminator 'kind'").contains("kind=summary");
    }

    @Test
    void should_use_simple_name_when_json_sub_types_name_is_omitted() {
        String json = """
            { "type": "MinimalOnly", "text": "hello" }
        """;

        ChatModelMock model = ChatModelMock.thatAlwaysResponds(json);

        MinimalSubTypeService service =
                AiServices.builder(MinimalSubTypeService.class).chatModel(model).build();

        MinimalSubTypeResponse response = service.reply("hi");

        assertThat(response).isInstanceOf(MinimalOnly.class);
        MinimalOnly text = (MinimalOnly) response;
        assertThat(text.type()).isEqualTo("MinimalOnly");
        assertThat(text.text()).isEqualTo("hello");

        String prompt = model.userMessageText();
        assertThat(prompt).contains("discriminator 'type'").contains("type=MinimalOnly");
    }

    @Test
    void should_fail_on_duplicate_discriminator_values() {
        ChatModelMock model = ChatModelMock.thatAlwaysResponds(
                """
                { "type": "dup", "text": "x" }
                """);

        DuplicateNameService service =
                AiServices.builder(DuplicateNameService.class).chatModel(model).build();

        assertThatThrownBy(() -> service.reply("hi"))
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessageContaining("Duplicate discriminator value");
    }
}
