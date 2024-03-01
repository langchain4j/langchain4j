package dev.langchain4j.rag.content.retriever.easy;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class EasyContentRetrieverTest {

    interface Assistant {

        String chat(String userMessage);
    }

    @Test
    void should_work() {

        // given
        String filePath = toAbsolutePath("story-about-happy-carrot.txt");

        ContentRetriever contentRetriever = EasyContentRetriever.fromFile(filePath);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(OpenAiChatModel.withApiKey(System.getenv("OPENAI_API_KEY")))
                .contentRetriever(contentRetriever)
                .build();

        // when
        String answer = assistant.chat("Who is Charlie?");

        // then
        assertThat(answer).containsIgnoringCase("carrot");
    }

    private String toAbsolutePath(String fileName) {
        try {
            return Paths.get(getClass().getClassLoader().getResource(fileName).toURI()).toString();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}