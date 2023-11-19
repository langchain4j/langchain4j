package dev.langchain4j.agen.tool.graal;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class GraalPolyglotAiServiceTool {

    public static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");

    interface Assistant {

        String chat(String userMessage);
    }


    @Test
    public void jsTool() {
        ChatLanguageModel model = OpenAiChatModel.builder()
                .apiKey(OPENAI_API_KEY)
                .logRequests(true)
                .build();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .tools(new GraalJavascriptExecutionTool())
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();

        Assertions.assertTrue(Boolean.parseBoolean(assistant.chat("Is 146837 a prime? Just return true or false")));
        Assertions.assertFalse(Boolean.parseBoolean(assistant.chat("Is 146955 a prime? Just return true or false")));
    }
    @Test
    public void pythonTool() {
        ChatLanguageModel model = OpenAiChatModel.builder()
                .apiKey(OPENAI_API_KEY)
                .logRequests(true)
                .build();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .tools(new GraalPythonExecutionTool())
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();

        Assertions.assertTrue(Boolean.parseBoolean(assistant.chat("Is 146837 a prime? Just return true or false")));
        Assertions.assertFalse(Boolean.parseBoolean(assistant.chat("Is 146955 a prime? Just return true or false")));
    }
}
