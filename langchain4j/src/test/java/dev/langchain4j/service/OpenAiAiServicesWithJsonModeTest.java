package dev.langchain4j.service;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.internal.OpenAiClient;
import dev.langchain4j.model.openai.internal.SyncOrAsyncOrStreaming;
import dev.langchain4j.model.openai.internal.chat.AssistantMessage;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionChoice;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionRequest;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionResponse;
import dev.langchain4j.model.openai.internal.chat.ResponseFormatType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Set;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_MODE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenAiAiServicesWithJsonModeTest {

    interface PersonExtractor1 {

        class Person {

            String name;
            int age;
            Double height;
            boolean married;
        }

        Person extractPersonFrom(String text);
    }

    @Test
    public void should_set_response_format_to_json_mode() throws Exception {
        OpenAiChatModel model = OpenAiChatModel.builder()
                .supportedCapabilities(Set.of(RESPONSE_FORMAT_JSON_MODE))
                .build();

        OpenAiClient mockClient = mock(OpenAiClient.class);

        SyncOrAsyncOrStreaming<ChatCompletionResponse> mockResponse = mock(SyncOrAsyncOrStreaming.class);
        when(mockClient.chatCompletion(any(ChatCompletionRequest.class))).thenReturn(mockResponse);

        ChatCompletionResponse emptyResponse = ChatCompletionResponse.builder()
                .choices(Collections.singletonList(ChatCompletionChoice.builder()
                        .message(AssistantMessage.builder()
                                .content("{\"name\":\"Klaus\",\"age\":37,\"height\":1.78,\"married\":false}")
                                .build())
                        .build()))
                .created((int) System.currentTimeMillis())
                .id("mock-response-id")
                .model("mock-model")
                .build();
        when(mockResponse.execute()).thenReturn(emptyResponse);

        Field clientField = OpenAiChatModel.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(model, mockClient);

        ArgumentCaptor<ChatCompletionRequest> requestCaptor = ArgumentCaptor.forClass(ChatCompletionRequest.class);

        PersonExtractor1 personExtractor = AiServices.builder(PersonExtractor1.class)
                .chatLanguageModel(model)
                .build();

        String text = "Klaus is 37 years old, 1.78m height and single";
        PersonExtractor1.Person person = personExtractor.extractPersonFrom(text);

        verify(mockClient).chatCompletion(requestCaptor.capture());

        ChatCompletionRequest capturedRequest = requestCaptor.getValue();

        assertThat(capturedRequest).isNotNull();
        assertThat(capturedRequest.responseFormat()).isNotNull();
        assertThat(capturedRequest.responseFormat().type()).isEqualTo(ResponseFormatType.JSON_OBJECT);
    }
}
