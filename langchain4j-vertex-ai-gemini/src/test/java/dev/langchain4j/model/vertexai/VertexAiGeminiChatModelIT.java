package dev.langchain4j.model.vertexai;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.generativeai.preview.GenerativeModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Disabled("To run this test, you must provide your own project and location")
class VertexAiGeminiChatModelIT {

    ChatLanguageModel model = VertexAiGeminiChatModel.builder()
            .project("langchain4j")
            .location("us-central1")
            .modelName("gemini-pro")
            .build();

    @Test
    void should_generate_response() {

        // given
        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        // when
        Response<AiMessage> response = model.generate(userMessage);
        System.out.println(response);

        // then
        assertThat(response.content().text()).contains("Berlin");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(7);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
    }

    @Test
    void should_deny_system_message() {

        // given
        SystemMessage systemMessage = SystemMessage.from("Be polite");
        UserMessage userMessage = UserMessage.from("Tell me a joke");

        // when-then
        assertThatThrownBy(() -> model.generate(systemMessage, userMessage))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("SystemMessage is currently not supported by Gemini");
    }

    @Test
    void should_respect_maxOutputTokens() {

        // given
        ChatLanguageModel model = VertexAiGeminiChatModel.builder()
                .project("langchain4j")
                .location("us-central1")
                .modelName("gemini-pro")
                .maxOutputTokens(1)
                .build();

        UserMessage userMessage = UserMessage.from("Tell me a joke");

        // when
        Response<AiMessage> response = model.generate(userMessage);
        System.out.println(response);

        // then
        assertThat(response.content().text()).isNotBlank();

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(4);
        assertThat(tokenUsage.outputTokenCount()).isEqualTo(1);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(LENGTH);
    }

    @Test
    void should_allow_custom_generativeModel_and_generationConfig() throws IOException {

        // given
        VertexAI vertexAi = new VertexAI("langchain4j", "us-central1");
        GenerativeModel generativeModel = new GenerativeModel("gemini-pro", vertexAi);
        GenerationConfig generationConfig = GenerationConfig.getDefaultInstance();

        ChatLanguageModel model = new VertexAiGeminiChatModel(generativeModel, generationConfig);

        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        // when
        Response<AiMessage> response = model.generate(userMessage);
        System.out.println(response);

        // then
        assertThat(response.content().text()).contains("Berlin");
    }


    @Test
    void should_maintain_a_conversation() {

        // given
        ChatLanguageModel model = VertexAiGeminiChatModel.builder()
            .project("langchain4j")
            .location("us-central1")
            .modelName("gemini-pro")
            .maxOutputTokens(1024)
            .build();

        ConversationalChain chain = ConversationalChain.builder()
            .chatLanguageModel(model)
            .build();

        // when/then
        String resp = chain.execute("What color is the sky usually?");
        assertThat(resp).containsIgnoringCase("blue");

        resp = chain.execute("Why is it of that color?");
        assertThat(resp).containsIgnoringCase("scattering");

        resp = chain.execute("What's the name of the scattering effect?");
        assertThat(resp).containsIgnoringCase("Rayleigh");
    }

    static class Person {
        String name;
        int age;
    }

    interface PersonExtractor {
        @dev.langchain4j.service.UserMessage(value = {
            "Extract the name and age of the person described below.",
            "Return a JSON document with a \"name\" and an \"age\" property, following this structure: ",
            "{\"name\": \"John Doe\", \"age\": 34}",
            "Return only JSON, without any markdown markup surrounding it.",
            "Here is the document describing the person:",
            "---",
            "{{it}}",
            "---",
            "JSON: "})
        Person extractPerson(String text);
    }

    @Test
    void should_extract_a_person() {

        // given
        ChatLanguageModel model = VertexAiGeminiChatModel.builder()
            .project("langchain4j")
            .location("us-central1")
            .modelName("gemini-pro")
            .maxOutputTokens(32)
            .build();

        PersonExtractor extractor = AiServices.create(PersonExtractor.class, model);

        // when
        Person person = extractor.extractPerson(
            "Anna is a 23 year old artist based in Brooklyn, New York. " +
            "She was born and raised in the suburbs of Chicago, where " +
            "she developed a love for art at a young age. She attended " +
            "the School of the Art Institute of Chicago, where she " +
            "studied painting and drawing. After graduating, she moved " +
            "to New York City to pursue her art career. Anna's work is " +
            "inspired by her personal experiences and observations of " +
            "the world around her. She often uses bright colors and " +
            "bold lines to create vibrant and energetic paintings. " +
            "Her work has been exhibited in galleries and museums in " +
            "New York City and Chicago.");

        // then
        assertThat(person.name).isEqualTo("Anna");
        assertThat(person.age).isEqualTo(23);
    }
}
