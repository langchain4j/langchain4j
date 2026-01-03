package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiServicesMultimodalTest {

    @Mock
    ChatModel chatModel;

    interface MultimodalAssistant {

        void chat(AudioContent audio);

        void chatWithAnnotation(@dev.langchain4j.service.UserMessage AudioContent audio);

        void chatWithList(List<Content> contents);
    }

    @Test
    void should_send_audio_content_as_is_without_annotation() {
        MultimodalAssistant assistant = AiServices.builder(MultimodalAssistant.class)
                .chatModel(chatModel)
                .build();

        String base64Data = "AAECAw==";
        AudioContent audioContent =
                AudioContent.from(Audio.builder().base64Data(base64Data).build());

        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("Response"))
                        .build());

        assistant.chat(audioContent);

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel).chat(requestCaptor.capture());

        ChatRequest request = requestCaptor.getValue();
        List<ChatMessage> messages = request.messages();

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).isInstanceOf(UserMessage.class);

        UserMessage userMessage = (UserMessage) messages.get(0);
        assertThat(userMessage.contents()).hasSize(1);
        assertThat(userMessage.contents().get(0)).isInstanceOf(AudioContent.class);

        AudioContent sentContent = (AudioContent) userMessage.contents().get(0);
        assertThat(sentContent.audio().base64Data()).isEqualTo(base64Data);
    }

    @Test
    void should_send_audio_content_as_is_with_annotation() {
        MultimodalAssistant assistant = AiServices.builder(MultimodalAssistant.class)
                .chatModel(chatModel)
                .build();

        AudioContent audioContent = AudioContent.from("AAECAw==");
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("Response"))
                        .build());

        assistant.chatWithAnnotation(audioContent);

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel).chat(requestCaptor.capture());

        UserMessage userMessage =
                (UserMessage) requestCaptor.getValue().messages().get(0);

        assertThat(userMessage.contents()).hasSize(1);
        assertThat(userMessage.contents().get(0)).isInstanceOf(AudioContent.class);
    }

    @Test
    void should_handle_list_of_contents() {
        MultimodalAssistant assistant = AiServices.builder(MultimodalAssistant.class)
                .chatModel(chatModel)
                .build();

        List<Content> contents = List.of(
                dev.langchain4j.data.message.TextContent.from("Analyze this audio:"), AudioContent.from("AAECAw=="));

        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("Response"))
                        .build());

        assistant.chatWithList(contents);

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel).chat(requestCaptor.capture());

        UserMessage userMessage =
                (UserMessage) requestCaptor.getValue().messages().get(0);
        assertThat(userMessage.contents()).hasSize(2);
        assertThat(userMessage.contents().get(0)).isInstanceOf(dev.langchain4j.data.message.TextContent.class);
        assertThat(userMessage.contents().get(1)).isInstanceOf(AudioContent.class);
    }
}
