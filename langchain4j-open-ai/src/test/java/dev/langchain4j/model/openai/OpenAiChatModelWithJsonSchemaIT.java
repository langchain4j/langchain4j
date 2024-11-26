package dev.langchain4j.model.openai;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

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
        final JsonObjectSchema circle = JsonObjectSchema.builder()
                .description("Circle")
                .properties(new LinkedHashMap<>() {{
                    put("radius", JsonNumberSchema.builder().build());
                }})
                .required(singletonList("radius"))
                .additionalProperties(false)
                .build();
        final JsonObjectSchema rectangle = JsonObjectSchema.builder()
                .description("Rectangle")
                .properties(new LinkedHashMap<>() {{
                    put("width", JsonNumberSchema.builder().build());
                    put("height", JsonNumberSchema.builder().build());
                }})
                .required(asList("width", "height"))
                .additionalProperties(false)
                .build();
        final JsonSchema jsonSchema = JsonSchema.builder()
                .name("shape")
                .rootElement(JsonObjectSchema.builder()
                        .description("Shape")
                        .properties(new LinkedHashMap<>() {{
                            put("shapes", JsonArraySchema.builder()
                                    .items(JsonAnyOfSchema.builder()
                                            .description("Shape")
                                            .anyOf(asList(circle, rectangle))
                                            .build())
                                    .build());
                        }})
                        .required(singletonList("shape"))
                        .additionalProperties(false)
                        .build())
                .build();


        final ChatResponse response = model.chat(ChatRequest.builder()
                .messages(userMessage("""
                        Extract information from the following text:
                        1. A circle with a radius of 5
                        2. A rectangle with a width of 10 and a height of 20
                        """))
                .responseFormat(ResponseFormat.builder()
                        .jsonSchema(jsonSchema)
                        .type(ResponseFormatType.JSON)
                        .build())
                .build());

        final Shapes shapes = new ObjectMapper().readValue(response.aiMessage().text(), Shapes.class);
        Assertions.assertThat(shapes).isNotNull();
        Assertions.assertThat(shapes.shapes())
                .isNotNull()
                .containsExactlyInAnyOrder(
                        new Circle(5),
                        new Rectangle(10, 20)
                );
    }

}
