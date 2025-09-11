package dev.langchain4j.memory.chat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.prompt.SummarizedSystemPrompt;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.structured.StructuredPromptProcessor;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import org.junit.jupiter.api.Test;

import static dev.langchain4j.model.openai.OpenAiModelName.GPT_3_5_TURBO;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SummarizedTokenWindowChatMemoryTest {

    private static final Tokenizer TOKENIZER = new OpenAiTokenizer(GPT_3_5_TURBO);



    ChatLanguageModel chatLanguageModel = OpenAiChatModel.builder()
//            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
//            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();


    @Test
    void test_summmarizedSystemMessage_parts_extraction() {
        String originalSystemMessage = "This is the original system message.";
        String summary = "An ongoing conversation between the user and the AI about starting a business.";
        SummarizedSystemPrompt summarizedMessage = new SummarizedSystemPrompt(originalSystemMessage, summary);
        Prompt prompt = StructuredPromptProcessor.toPrompt(summarizedMessage);
        SystemMessage summarizedSystemMessage = prompt.toSystemMessage();
        String summarizedStringVersion = prompt.toString();

        SummarizedTokenWindowChatMemory memory = SummarizedTokenWindowChatMemory.builder()
                .id("test")
                .maxTokens(100, TOKENIZER)
                .chatLanguageModel(mock(ChatLanguageModel.class))
                .build();

        String extractedExistingSummary = memory.extractSummary(summarizedSystemMessage).get();
        String extractedOriginalSystemMessage = memory.extractOriginalSystemMessage(summarizedSystemMessage).get();
        assertEquals(summary, extractedExistingSummary);
        assertEquals(originalSystemMessage, extractedOriginalSystemMessage);

    }

    // TODO this is an IT test, not a unit test
    @Test
    void test_summary_creation() {
        ChatMemoryStore inMemoryChatMemoryStore = new InMemoryChatMemoryStore();
        SummarizedTokenWindowChatMemory memory = SummarizedTokenWindowChatMemory.builder()
                .id("test")
                .maxTokens(200, TOKENIZER)
                .chatLanguageModel(chatLanguageModel)
                .chatMemoryStore(inMemoryChatMemoryStore)
                .build();

        ChatMessage m1 = SystemMessage.from("You are a helpful conversational AI.");
        ChatMessage m2 = UserMessage.from("Tell me about contributing to open source");
        ChatMessage m3 = AiMessage.from("Open source refers to software whose source code is freely available for anyone to view, modify, and distribute, fostering a collaborative approach to development that promotes transparency, innovation, and community involvement. This model, governed by specific licenses, allows developers worldwide to contribute to projects, often resulting in more secure, customizable, and rapidly evolving software. Popular examples like Linux, Firefox, and WordPress demonstrate the power of open source in creating robust, widely-adopted solutions. While typically free to use, some open source projects offer paid versions or support. The open source philosophy extends beyond software, influencing fields such as hardware design, scientific research, and content creation, embodying principles of openness, sharing, and collective progress in the digital age.\n");
        ChatMessage m4 = UserMessage.from("How can I get started?");

        memory.add(m1);
        memory.add(m2);
        memory.add(m3);
        memory.add(m4);

        // The first user message should be dropped & integrated in the summary.
        assertEquals(inMemoryChatMemoryStore.getMessages("test").size(), 3);

    }

}