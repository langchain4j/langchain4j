package dev.langchain4j.service;

import static dev.langchain4j.service.AiServicesIT.verifyNoMoreInteractionsFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AiServicesWithCustomContentInjectorTest {

    @Spy
    ChatModel chatModel = ChatModelMock.thatAlwaysResponds("{\"value\":\"test\"}");

    @AfterEach
    void afterEach() {
        verifyNoMoreInteractionsFor(chatModel);
    }

    private static final Image image = Image.builder()
            .url("https://en.wikipedia.org/wiki/Llama#/media/File:Llamas,_Vernagt-Stausee,_Italy.jpg")
            .build();

    private static final ImageContent imageContent = ImageContent.from(image);

    static class MyStructuredOutput {

        private String value;

        MyStructuredOutput() {}

        MyStructuredOutput(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    interface MyAiService {
        MyStructuredOutput chat(@UserMessage String text);
    }

    @Test
    void should_append_output_format_instructions_to_the_last_text_content() {
        // given
        ContentInjector customContentInjector = (contents, chatMessage) -> {
            return dev.langchain4j.data.message.UserMessage.from(
                    TextContent.from("First text"), imageContent, TextContent.from("Second text"));
        };

        ContentRetriever customContentRetriever = query -> Collections.emptyList();

        RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                .contentRetriever(customContentRetriever)
                .contentInjector(customContentInjector)
                .build();

        MyAiService myAiService = AiServices.builder(MyAiService.class)
                .chatModel(chatModel)
                .retrievalAugmentor(retrievalAugmentor)
                .build();

        // when
        myAiService.chat("How many lamas are there in this image?");
        ArgumentCaptor<ChatRequest> chatRequestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel).chat(chatRequestCaptor.capture());

        dev.langchain4j.data.message.UserMessage userMessage = (dev.langchain4j.data.message.UserMessage)
                chatRequestCaptor.getValue().messages().get(0);

        assertThat(userMessage.contents()).hasSize(3);
        assertThat(userMessage.contents().get(0)).isInstanceOf(TextContent.class);
        assertThat(userMessage.contents().get(1)).isInstanceOf(ImageContent.class);
        assertThat(userMessage.contents().get(2)).isInstanceOf(TextContent.class);

        TextContent text1 = (TextContent) userMessage.contents().get(0);
        TextContent text2 = (TextContent) userMessage.contents().get(2);
        assertThat(text1.text()).isEqualTo("First text");
        assertThat(text2.text())
                .contains("Second text")
                .contains("You must answer strictly in the following JSON format");

        verify(chatModel).supportedCapabilities();
    }

    @Test
    void should_create_new_text_content_of_output_format_instructions_if_there_is_no_text_content() {
        // given
        ContentInjector customContentInjector = (contents, chatMessage) -> {
            return dev.langchain4j.data.message.UserMessage.from(imageContent, imageContent);
        };

        ContentRetriever customContentRetriever = query -> Collections.emptyList();

        RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                .contentRetriever(customContentRetriever)
                .contentInjector(customContentInjector)
                .build();

        MyAiService myAiService = AiServices.builder(MyAiService.class)
                .chatModel(chatModel)
                .retrievalAugmentor(retrievalAugmentor)
                .build();

        // when
        myAiService.chat("How many lamas are there in this image?");
        ArgumentCaptor<ChatRequest> chatRequestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel).chat(chatRequestCaptor.capture());

        dev.langchain4j.data.message.UserMessage userMessage = (dev.langchain4j.data.message.UserMessage)
                chatRequestCaptor.getValue().messages().get(0);

        assertThat(userMessage.contents()).hasSize(3);
        assertThat(userMessage.contents().get(0)).isInstanceOf(ImageContent.class);
        assertThat(userMessage.contents().get(1)).isInstanceOf(ImageContent.class);
        assertThat(userMessage.contents().get(2)).isInstanceOf(TextContent.class);

        TextContent textContent = (TextContent) userMessage.contents().get(2);
        assertThat(textContent.text()).contains("You must answer strictly in the following JSON format");

        verify(chatModel).supportedCapabilities();
    }
}
