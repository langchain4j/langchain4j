package dev.langchain4j.model.openaiofficial.openai;

import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;
import static java.util.List.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.common.AbstractAiServiceWithJsonSchemaIT;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiOfficialResponsesAiServicesWithJsonSchemaIT extends AbstractAiServiceWithJsonSchemaIT {

    private static final ChatModel STRICT_MODEL = new StreamingChatModelAdapter(
            OpenAiOfficialResponsesStreamingChatModel.builder()
                    .apiKey(System.getenv("OPENAI_API_KEY"))
                    .modelName(InternalOpenAiOfficialTestHelper.CHAT_MODEL_NAME.toString())
                    .temperature(0.0)
                    .strict(true)
                    .build(),
            true);

    // Responses API validates schemas as strict, even when strict=false.
    private static final ChatModel NON_STRICT_MODEL = new StreamingChatModelAdapter(
            OpenAiOfficialResponsesStreamingChatModel.builder()
                    .apiKey(System.getenv("OPENAI_API_KEY"))
                    .modelName(InternalOpenAiOfficialTestHelper.CHAT_MODEL_NAME.toString())
                    .temperature(0.0)
                    .strict(false)
                    .build(),
            true);

    @Override
    protected List<ChatModel> models() {
        return List.of(STRICT_MODEL, NON_STRICT_MODEL);
    }

    @Override
    protected boolean supportsRecursion() {
        return true;
    }

    @Override
    protected boolean isStrictJsonSchemaEnabled(ChatModel model) {
        return model instanceof StreamingChatModelAdapter adapter && adapter.strictJsonSchemaEnabled;
    }

    @ParameterizedTest
    @MethodSource("models")
    @Override
    protected void should_extract_pojo_with_local_date_time_fields(ChatModel model) {
        model = spy(model);

        interface PersonExtractor {
            class Person {
                String name;
                LocalDate birthDate;
                LocalTime birthTime;
                LocalDateTime birthDateTime;
            }

            Person extractPersonFrom(String text);
        }

        PersonExtractor personExtractor = AiServices.builder(PersonExtractor.class)
                .chatModel(model)
                .systemMessage("Fill all fields when information is present. Never return nulls.")
                .build();

        String text = "Extract the person's information from the following text. "
                + "Fill in all the fields where the information is available! "
                + "Text: 'Klaus was born at 14:43 on 12th of August 1976'";

        PersonExtractor.Person person = personExtractor.extractPersonFrom(text);

        assertThat(person.name).isEqualTo("Klaus");
        assertThat(person.birthDate).isEqualTo(LocalDate.of(1976, 8, 12));
        assertThat(person.birthTime).isEqualTo(LocalTime.of(14, 43));
        assertThat(person.birthDateTime).isEqualTo(LocalDateTime.of(1976, 8, 12, 14, 43));

        verify(model)
                .chat(ChatRequest.builder()
                        .messages(of(
                                systemMessage("Fill all fields when information is present. Never return nulls."),
                                userMessage(text)))
                        .responseFormat(ResponseFormat.builder()
                                .type(JSON)
                                .jsonSchema(JsonSchema.builder()
                                        .name("Person")
                                        .rootElement(JsonObjectSchema.builder()
                                                .addProperties(new LinkedHashMap<>() {
                                                    {
                                                        put("name", new JsonStringSchema());
                                                        put(
                                                                "birthDate",
                                                                JsonObjectSchema.builder()
                                                                        .addProperties(new LinkedHashMap<>() {
                                                                            {
                                                                                put("year", new JsonIntegerSchema());
                                                                                put("month", new JsonIntegerSchema());
                                                                                put("day", new JsonIntegerSchema());
                                                                            }
                                                                        })
                                                                        .build());
                                                        put(
                                                                "birthTime",
                                                                JsonObjectSchema.builder()
                                                                        .addProperties(new LinkedHashMap<>() {
                                                                            {
                                                                                put("hour", new JsonIntegerSchema());
                                                                                put("minute", new JsonIntegerSchema());
                                                                                put("second", new JsonIntegerSchema());
                                                                                put("nano", new JsonIntegerSchema());
                                                                            }
                                                                        })
                                                                        .build());
                                                        put(
                                                                "birthDateTime",
                                                                JsonObjectSchema.builder()
                                                                        .addProperties(new LinkedHashMap<>() {
                                                                            {
                                                                                put(
                                                                                        "date",
                                                                                        JsonObjectSchema.builder()
                                                                                                .addProperties(
                                                                                                        new LinkedHashMap<>() {
                                                                                                            {
                                                                                                                put(
                                                                                                                        "year",
                                                                                                                        new JsonIntegerSchema());
                                                                                                                put(
                                                                                                                        "month",
                                                                                                                        new JsonIntegerSchema());
                                                                                                                put(
                                                                                                                        "day",
                                                                                                                        new JsonIntegerSchema());
                                                                                                            }
                                                                                                        })
                                                                                                .build());
                                                                                put(
                                                                                        "time",
                                                                                        JsonObjectSchema.builder()
                                                                                                .addProperties(
                                                                                                        new LinkedHashMap<>() {
                                                                                                            {
                                                                                                                put(
                                                                                                                        "hour",
                                                                                                                        new JsonIntegerSchema());
                                                                                                                put(
                                                                                                                        "minute",
                                                                                                                        new JsonIntegerSchema());
                                                                                                                put(
                                                                                                                        "second",
                                                                                                                        new JsonIntegerSchema());
                                                                                                                put(
                                                                                                                        "nano",
                                                                                                                        new JsonIntegerSchema());
                                                                                                            }
                                                                                                        })
                                                                                                .build());
                                                                            }
                                                                        })
                                                                        .build());
                                                    }
                                                })
                                                .build())
                                        .build())
                                .build())
                        .build());
        verify(model).supportedCapabilities();
    }

    private static class StreamingChatModelAdapter implements ChatModel {

        private final StreamingChatModel streamingChatModel;
        private final boolean strictJsonSchemaEnabled;

        private StreamingChatModelAdapter(StreamingChatModel streamingChatModel, boolean strictJsonSchemaEnabled) {
            this.streamingChatModel = streamingChatModel;
            this.strictJsonSchemaEnabled = strictJsonSchemaEnabled;
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
            streamingChatModel.chat(chatRequest, handler);
            return handler.get();
        }

        @Override
        public ChatRequestParameters defaultRequestParameters() {
            return streamingChatModel.defaultRequestParameters();
        }

        @Override
        public List<ChatModelListener> listeners() {
            return streamingChatModel.listeners();
        }

        @Override
        public ModelProvider provider() {
            return streamingChatModel.provider();
        }

        @Override
        public Set<Capability> supportedCapabilities() {
            return streamingChatModel.supportedCapabilities();
        }
    }
}
