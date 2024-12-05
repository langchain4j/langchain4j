package dev.langchain4j.model.openai;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;
import static dev.langchain4j.model.chat.request.json.JsonSchemaElementHelper.jsonSchemaElementFrom;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static org.assertj.core.api.Assertions.assertThat;

class OpenAiChatModelWithJsonSchemaIT {

    OpenAiChatModel model = OpenAiChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .modelName(GPT_4_O_MINI)
            .strictJsonSchema(true)
            .logRequests(true)
            .logResponses(true)
            .build();

    @JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
    @JsonSubTypes({
            @JsonSubTypes.Type(Circle.class),
            @JsonSubTypes.Type(Rectangle.class)
    })
    interface Shape {

    }

    record Circle(double radius) implements Shape {

    }

    record Rectangle(double width,
                     double height) implements Shape {

    }

    record Shapes(List<Shape> shapes) {
    }

    @Test
    void should_generate_valid_json_with_anyof() throws JsonProcessingException {

        // given
        JsonSchemaElement circleSchema = jsonSchemaElementFrom(Circle.class);
        JsonSchemaElement rectangleSchema = jsonSchemaElementFrom(Rectangle.class);

        JsonSchema jsonSchema = JsonSchema.builder()
                .name("Shapes")
                .rootElement(JsonObjectSchema.builder()
                        .addProperty("shapes", JsonArraySchema.builder()
                                .items(JsonAnyOfSchema.builder()
                                        .anyOf(circleSchema, rectangleSchema)
                                        .build())
                                .build())
                        .required(List.of("shapes"))
                        .build())
                .build();

        ResponseFormat responseFormat = ResponseFormat.builder()
                .type(JSON)
                .jsonSchema(jsonSchema)
                .build();

        UserMessage userMessage = UserMessage.from("""
                Extract information from the following text:
                1. A circle with a radius of 5
                2. A rectangle with a width of 10 and a height of 20
                """);

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(userMessage)
                .responseFormat(responseFormat)
                .build();

        // when
        ChatResponse chatResponse = model.chat(chatRequest);

        // then
        Shapes shapes = new ObjectMapper().readValue(chatResponse.aiMessage().text(), Shapes.class);
        assertThat(shapes).isNotNull();
        assertThat(shapes.shapes())
                .isNotNull()
                .containsExactlyInAnyOrder(
                        new Circle(5),
                        new Rectangle(10, 20)
                );
    }
}
