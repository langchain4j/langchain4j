package dev.langchain4j.service.common.openai;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.common.AbstractAiServiceWithJsonSchemaIT;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

// TODO move to langchain4j-open-ai module once dependency cycle is resolved
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiAiServiceWithJsonSchemaIT extends AbstractAiServiceWithJsonSchemaIT {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", visible = true)
    @JsonSubTypes({
        @JsonSubTypes.Type(value = TextResponse.class, name = "text"),
        @JsonSubTypes.Type(value = ImageResponse.class, name = "image")
    })
    interface ChatbotResponse {}

    record TextResponse(String type, String text) implements ChatbotResponse {}

    record ImageResponse(String type, String url) implements ChatbotResponse {}

    interface ChatbotService {

        ChatbotResponse respond(String text);

        List<ChatbotResponse> respondWithList(String text);
    }

    OpenAiChatModel modelWithStrictJsonSchema = OpenAiChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .modelName(GPT_4_O_MINI)
            .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
            .strictJsonSchema(true)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    OpenAiChatModel modelWithStrictJsonSchemaLegacy = OpenAiChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .modelName(GPT_4_O_MINI)
            .responseFormat("json_schema") // testing backward compatibility
            .strictJsonSchema(true)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Override
    protected List<ChatModel> models() {
        return List.of(
                modelWithStrictJsonSchema,
                modelWithStrictJsonSchemaLegacy,
                OpenAiChatModel.builder()
                        .baseUrl(System.getenv("OPENAI_BASE_URL"))
                        .apiKey(System.getenv("OPENAI_API_KEY"))
                        .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                        .modelName(GPT_4_O_MINI)
                        .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
                        .strictJsonSchema(false)
                        .temperature(0.0)
                        .logRequests(true)
                        .logResponses(true)
                        .build(),
                OpenAiChatModel.builder()
                        .baseUrl(System.getenv("OPENAI_BASE_URL"))
                        .apiKey(System.getenv("OPENAI_API_KEY"))
                        .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                        .modelName(GPT_4_O_MINI)
                        .responseFormat("json_schema") // testing backward compatibility
                        .strictJsonSchema(false)
                        .temperature(0.0)
                        .logRequests(true)
                        .logResponses(true)
                        .build());
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_extract_polymorphic_response_text(ChatModel model) {
        ChatbotService service = AiServices.create(ChatbotService.class, model);

        String text = "Respond only with a text response. Use type 'text' and include a short greeting in 'text'.";

        ChatbotResponse response = service.respond(text);

        assertThat(response).isInstanceOf(TextResponse.class);
        TextResponse textResponse = (TextResponse) response;
        assertThat(textResponse.type()).isEqualTo("text");
        assertThat(textResponse.text()).isNotBlank();
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_extract_polymorphic_response_image(ChatModel model) {
        ChatbotService service = AiServices.create(ChatbotService.class, model);

        String text = "Respond only with an image response. Use type 'image' and include a dummy url in 'url'.";

        ChatbotResponse response = service.respond(text);

        assertThat(response).isInstanceOf(ImageResponse.class);
        ImageResponse imageResponse = (ImageResponse) response;
        assertThat(imageResponse.type()).isEqualTo("image");
        assertThat(imageResponse.url()).isNotBlank();
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_extract_polymorphic_list_response(ChatModel model) {
        ChatbotService service = AiServices.create(ChatbotService.class, model);

        String text = "Respond with a list containing one text response (greeting) and one image response (dummy url).";

        List<ChatbotResponse> responses = service.respondWithList(text);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0)).isInstanceOf(TextResponse.class);
        assertThat(responses.get(1)).isInstanceOf(ImageResponse.class);
    }

    @Override
    protected boolean supportsRecursion() {
        return true;
    }

    @Override
    protected boolean isStrictJsonSchemaEnabled(ChatModel model) {
        return model == modelWithStrictJsonSchema || model == modelWithStrictJsonSchemaLegacy;
    }
}
