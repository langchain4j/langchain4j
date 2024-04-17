package dev.langchain4j.rag.retriever;

import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.Test;

import java.util.List;

import static java.lang.System.getenv;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;

class TavilyContentRetrieverIT {
    @Test
    void tavily_should_search_split_and_embed() {
        //given
        DocumentSplitter splitter = DocumentSplitters.recursive(500, 0);
        EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

        TavilyContentRetriever tavilyContentRetriever =
                TavilyContentRetriever.builder()
                        .baseUrl("https://api.tavily.com")
                        .apiKey(getenv("TAVILY_API_KEY"))
                        .timeout(ofSeconds(60))
                        .embeddingModelAndSlitter(embeddingModel, splitter)
                        .build();

        //when
        List<Content> contents = tavilyContentRetriever.retrieve(new Query("what is LangChain4j?"));

        //then
        assertThat(contents).isNotNull();
        contents.stream().map(Content::toString).forEach(System.out::println);
    }

    @Test
    void tavily_should_only_search() {
        //given
        TavilyContentRetriever tavilyContentRetriever =
                TavilyContentRetriever.withApiKey(getenv("TAVILY_API_KEY"));

        //when
        List<Content> contents = tavilyContentRetriever.retrieve(new Query("Has LangChain4j? chat memory"));

        //then
        assertThat(contents).isNotNull();
        contents.stream().map(Content::toString).forEach(System.out::println);
    }

}