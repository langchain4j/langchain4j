package dev.langchain4j.model.watsonx;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class WatsonxAiChatModelTest {


    @Test
    void testAiChatModel() {

        ChatLanguageModel languageModel = new WatsonxAiChatModel(
            "https://eu-gb.ml.cloud.ibm.com/ml/v1",
            "2023-10-25",
            System.getenv("WATSONX_API_KEY"),
            Duration.ofSeconds(30),
            true,
            true,
            null,
            "674ddd9d-cd38-454b-9b35-ab207fcbd057",
            "meta-llama/llama-3-8b-instruct",
            null
        );



        Response<AiMessage> aiMessageResponse = languageModel.generate(new UserMessage("Tell me a joke"));

        assertNotNull(aiMessageResponse);
        assertNotNull(aiMessageResponse.content());
    }
}
