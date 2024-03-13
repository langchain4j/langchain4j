package dev.langchain4j.rag.easy;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class EasyRAGTest {

    private static final String EMBEDDING_STORE_PATH = "store.json";

    OpenAiChatModel model = OpenAiChatModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .logRequests(true)
            .logResponses(true)
            .build();

    InMemoryEmbeddingStore<TextSegment> embeddingStore;

    interface Assistant {

        String answer(String query);
    }

    @BeforeEach
    void beforeEach() {


    }

    // TODO decisions:

    // TODO ingestion:
    // TODO - how to pre-process/clean documents? html: remove html tags. markdown: remove special chars
    // TODO - how to split documents? depending on the document type: take into account html/md formatting, pages in pdf, etc
    // TODO - which segment size and overlap to choose? chars or tokens? make bigger overlap and then auto-merge?
    // TODO - how to post-process/enrich segments (e.g. include document/chapter title/header/summary/etc into each segment)?
    // TODO - which embedding model to use? local, cloud, etc. check MTEB. make configurable in v2?
    // TODO - which embedding store to use? local, cloud, etc make configurable in v2?

    // TODO retrieval:
    // TODO - how to retrieve? do query transformations? do metadata filtering? do hybrid retrieval?
    // TODO - how much to retrieve (in the number of segments or the number of tokens) <= make configurable!
    // TODO - re-rank automatically? (maybe in the future, with local cross-encoders)
    // TODO

    @Test
    void RAG_should_be_easy() {

        String filePath = toAbsolutePath("story-about-happy-carrot.txt");
        embeddingStore = EasyRAG.ingestFile(filePath);
        embeddingStore.serializeToFile(EMBEDDING_STORE_PATH);


        // or
        IngestionConfig config = new IngestionConfig(100, 10);
        InMemoryEmbeddingStore<TextSegment> es2 = EasyRAG.ingestFile(filePath, config);
        // or
        InMemoryEmbeddingStore<TextSegment> es3 = EasyRAG.ingestDirectory("C:\\dev", "*.pdf");
        // or
        InMemoryEmbeddingStore<TextSegment> es4 = EasyRAG.ingestDirectoryRecursively("C:\\dev", "**.pdf");


        InMemoryEmbeddingStore<TextSegment> embeddingStore = InMemoryEmbeddingStore.fromFile(EMBEDDING_STORE_PATH);

        ContentRetriever contentRetriever = EasyRAG.createContentRetriever(embeddingStore);
        // or
        ContentRetriever contentRetriever2 = EasyRAG.createContentRetriever(embeddingStore, new RetrievalConfig(3, 0.5));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .contentRetriever(contentRetriever)
                .build();

        String answer = assistant.answer("Who is Charlie?");

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