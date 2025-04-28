package dev.langchain4j.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static dev.langchain4j.service.AiServicesIT.verifyNoMoreInteractionsFor;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class AiServicesWithJsonSchemaWithRequiredIT {

    @Spy
    ChatModel model = OpenAiChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .modelName(GPT_4_O_MINI)
            .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    @AfterEach
    void afterEach() {
        verifyNoMoreInteractionsFor(model);
    }

    /**
     * NOTE:
     * When used with the "structured outputs" feature, all POJO fields and sub-fields
     * are considered <b>optional</b> by default.
     * This is different from "tools" (see {@link AiServicesWithToolsWithRequiredIT}),
     * where all fields and sub-fields are considered <b>required</b> by default.
     */
    @Test
    void should_extract_pojo_with_required_field() {

        // given
        interface PersonExtractor {

            class Person {

                @JsonProperty(required = true)
                String name;

                Integer age;
            }

            Person extractPersonFrom(String text);
        }

        PersonExtractor personExtractor = AiServices.create(PersonExtractor.class, model);

        String text = "Klaus is 37 years old";

        // when
        PersonExtractor.Person person = personExtractor.extractPersonFrom(text);

        // then
        assertThat(person.name).isEqualTo("Klaus");
        assertThat(person.age).isEqualTo(37);

        verify(model).chat(ChatRequest.builder()
                .messages(singletonList(userMessage(text)))
                .responseFormat(ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(JsonSchema.builder()
                                .name("Person")
                                .rootElement(JsonObjectSchema.builder()
                                        .addStringProperty("name")
                                        .addIntegerProperty("age")
                                        .required("name")
                                        .build())
                                .build())
                        .build())
                .build());
    }
}
