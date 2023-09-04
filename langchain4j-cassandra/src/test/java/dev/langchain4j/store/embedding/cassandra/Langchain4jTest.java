package dev.langchain4j.store.embedding.cassandra;

import dev.langchain4j.chain.ConversationalChain;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.Test;
import static dev.langchain4j.data.document.FileSystemDocumentLoader.loadDocument;

import static dev.langchain4j.model.openai.OpenAiModelName.GPT_3_5_TURBO;
import static java.time.Duration.ofSeconds;
import static dev.langchain4j.data.message.UserMessage.userMessage;

public class Langchain4jTest {

    private static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");

    private static final ChatLanguageModel OPENAI_GPT_3_5_TURBO_MODEL = OpenAiChatModel.builder()
            .apiKey(OPENAI_API_KEY)
            .modelName(GPT_3_5_TURBO)
            .temperature(0.3)
            .timeout(ofSeconds(120))
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    public void shouldPromptOpenAI() {
        OpenAiChatModel model = OpenAiChatModel.withApiKey("demo");
        AiMessage answer = model.sendUserMessage("What is FF4j ?");
        System.out.println(answer.text());
    }

    interface Assistant {
        String chat(String message);
    }

    @Test
    public void simpleAssistantTest() {
        Assistant assistant = AiServices.create(Assistant.class, OPENAI_GPT_3_5_TURBO_MODEL);
        System.out.println(assistant.chat("What is FF4j ?"));
    }

    @Test
    public void chatMemorySimpleTest() {
        ConversationalChain chain = ConversationalChain.builder()
                .chatLanguageModel(OPENAI_GPT_3_5_TURBO_MODEL)
                .build();
        System.out.println(chain.execute("What is FF4j ?"));
        System.out.println(chain.execute("Does it provide a REST API ?"));
    }

    @Test
    public void chatMemoryDetailedTest() {
        Tokenizer tokenizer = new OpenAiTokenizer(GPT_3_5_TURBO);
        ChatMemory chatMemory = TokenWindowChatMemory.withMaxTokens(300, tokenizer);
        chatMemory.add(userMessage("All following questions are about FF4j, try to provide URL if you can"));
        chatMemory.add(OPENAI_GPT_3_5_TURBO_MODEL.sendMessages(chatMemory.messages()));
        chatMemory.add(userMessage("Does it provide a REST API ?"));
        System.out.println(OPENAI_GPT_3_5_TURBO_MODEL.sendMessages(chatMemory.messages()).text());
    }


}
