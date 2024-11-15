package dev.langchain4j.llama3;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class Llama3ChatLanguageModelTest {


    @Test
    void testLlama3ChatLanguageModel() throws IOException {

        ChatLanguageModel languageModel = new Llama3ChatLanguageModel("Meta-Llama-3-8B-Instruct-Q4_0.gguf");

        languageModel.generate(new UserMessage("tell me a joke"));
    }
}
