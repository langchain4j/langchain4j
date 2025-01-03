package dev.langchain4j.model.azure;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.AiServicesWithJsonSchemaIT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class AzureOpenAiAiServicesWithJsonSchemaIT extends AiServicesWithJsonSchemaIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(
                AzureOpenAiChatModel.builder()
                        .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                        .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                        .deploymentName("gpt-4o-mini")
                        .supportedCapabilities(Set.of(RESPONSE_FORMAT_JSON_SCHEMA))
                        .strictJsonSchema(true)
                        .temperature(0.0)
                        .logRequestsAndResponses(true)
                        .build(),
                AzureOpenAiChatModel.builder()
                        .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                        .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                        .deploymentName("gpt-4o-mini")
                        .supportedCapabilities(Set.of(RESPONSE_FORMAT_JSON_SCHEMA))
                        .strictJsonSchema(false)
                        .temperature(0.0)
                        .logRequestsAndResponses(true)
                        .build()
        );
    }

    @Override
    protected boolean supportsRecursion() {
        return true;
    }

    interface Assistant {
        Person chat(String text);
    }

    class Person {
        String name;
        int age;
        Double height;
        boolean married;
    }

    @Test
    protected void should_support_ai_services() {
        for (ChatLanguageModel model : models()) {
            String text = "Klaus is 37 years old, 1.78m height and single";

            // given
            model = spy(model);
            Assistant assistant = AiServices.create(Assistant.class, model);

            Person person = assistant.chat(text);
            assertThat(person.name).isEqualTo("Klaus");
            assertThat(person.age).isEqualTo(37);
            assertThat(person.height).isEqualTo(1.78);
            assertThat(person.married).isFalse();

            verify(model).chat(ChatRequest.builder()
                    .messages(singletonList(userMessage(text)))
                    .responseFormat(ResponseFormat.builder()
                            .type(JSON)
                            .jsonSchema(JsonSchema.builder()
                                    .name("Person")
                                    .rootElement(JsonObjectSchema.builder()
                                            .addStringProperty("name")
                                            .addIntegerProperty("age")
                                            .addNumberProperty("height")
                                            .addBooleanProperty("married")
                                            .required("name", "age", "height", "married")
                                            .build())
                                    .build())
                            .build())
                    .build());
        }
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_AZURE_OPENAI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
