package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.query.transformer.CompressingQueryTransformer;
import dev.langchain4j.test.mock.MockChatModel;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MultimodalRagAiServiceTest {

    private static final String QUESTION = "What is the warranty for this model?";
    private static final String WARRANTY = "This model has a two-year warranty.";
    private final ImageContent image = ImageContent.from("https://example.com/product.jpg");

    interface Assistant {

        String withParts(List<Content> parts);
    }

    private final Assistant assistant = AiServices.builder(Assistant.class)
            .chatModel(new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest request) {
                    dev.langchain4j.data.message.UserMessage message = (dev.langchain4j.data.message.UserMessage)
                            request.messages().get(0);
                    assertThat(message.contents())
                            .satisfiesExactly(
                                    text -> assertThat(text)
                                            .isInstanceOfSatisfying(
                                                    TextContent.class,
                                                    part -> assertThat(part.text())
                                                            .contains(QUESTION, WARRANTY)),
                                    part -> assertThat(part).isEqualTo(image));
                    return ChatResponse.builder()
                            .aiMessage(AiMessage.from("OK"))
                            .build();
                }
            })
            .contentRetriever(query -> {
                assertThat(query.text()).isEqualTo(QUESTION);
                return List.of(dev.langchain4j.rag.content.Content.from(WARRANTY));
            })
            .build();

    @Test
    void should_augment_question_and_image_in_a_content_list() {
        assertThat(assistant.withParts(List.of(TextContent.from(QUESTION), image)))
                .isEqualTo("OK");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void should_compress_a_follow_up_query_after_a_multimodal_turn(boolean storeRetrievedContent) {
        String followUp = "Does it cover accidental damage?";
        String compressed = "Does this model's warranty cover accidental damage?";
        MockChatModel compressionModel = MockChatModel.thatResponds(compressed);
        MockChatModel chatModel = MockChatModel.thatResponds("OK").thenRepeats();
        List<String> queries = new ArrayList<>();
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .storeRetrievedContentInChatMemory(storeRetrievedContent)
                .retrievalAugmentor(DefaultRetrievalAugmentor.builder()
                        .queryTransformer(new CompressingQueryTransformer(compressionModel))
                        .contentRetriever(query -> {
                            queries.add(query.text());
                            return List.of(dev.langchain4j.rag.content.Content.from(WARRANTY));
                        })
                        .build())
                .build();

        assertThat(assistant.withParts(List.of(TextContent.from(QUESTION), image)))
                .isEqualTo("OK");
        assertThat(assistant.withParts(List.of(TextContent.from(followUp)))).isEqualTo("OK");

        assertThat(queries).containsExactly(QUESTION, compressed);
        dev.langchain4j.data.message.UserMessage compressionPrompt = (dev.langchain4j.data.message.UserMessage)
                compressionModel.request().messages().get(0);
        assertThat(compressionPrompt.singleText()).contains("User: " + QUESTION, "AI: OK", "User query: " + followUp);
        dev.langchain4j.data.message.UserMessage previousUserMessage = (dev.langchain4j.data.message.UserMessage)
                chatModel.lastRequest().messages().get(0);
        assertThat(previousUserMessage.contents()).contains(image);
    }
}
