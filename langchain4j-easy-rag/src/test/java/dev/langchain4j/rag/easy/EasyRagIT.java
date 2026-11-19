package dev.langchain4j.rag.easy;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.MarkdownSectionSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class EasyRagIT {

    OpenAiChatModel model = OpenAiChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .modelName(GPT_4_O_MINI)
            .logRequests(true)
            .logResponses(true)
            .build();

    interface Assistant {

        String chat(String userMessage);
    }

    @Test
    void RAG_should_be_easy_to_setup() {

        Document document = FileSystemDocumentLoader.loadDocument(toPath("miles-of-smiles-terms-of-use.txt"));

        EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

        EmbeddingStoreIngestor.ingest(document, embeddingStore);
        // or
        EmbeddingStoreIngestor.builder()
                // .documentTransformer(...)
                // .documentSplitter(...)
                // .textSegmentTransformer(...)
                // .embeddingModel(...)
                .embeddingStore(embeddingStore)
                .build()
                .ingest(document);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .contentRetriever(EmbeddingStoreContentRetriever.from(embeddingStore))
                .build();

        String answer = assistant.chat("How many days before the rental can I cancel my booking?");

        assertThat(answer).containsAnyOf("17", "61");
    }

    @Test
    void RAG_with_markdown_files_should_be_easy_to_setup() {

        Document document = FileSystemDocumentLoader.loadDocument(toPath("miles-of-smiles-terms-of-use.md"));

        EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

        EmbeddingStoreIngestor.ingest(document, embeddingStore);
        // or
        EmbeddingStoreIngestor.builder()
                // .documentTransformer(...)
                .documentSplitter(MarkdownSectionSplitter.builder().build())
                // .textSegmentTransformer(...)
                // .embeddingModel(...)
                .embeddingStore(embeddingStore)
                .build()
                .ingest(document);

        final EmbeddingStoreContentRetriever contentRetriever = EmbeddingStoreContentRetriever.from(embeddingStore);
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .contentRetriever(contentRetriever)
                .build();

        String answer = assistant.chat("How many days before the rental can I cancel my booking?");

        assertThat(answer).containsAnyOf("17", "61");
    }

    // NOTE: This test must be run from the project root, not the module root
    @Test
    void RAG_with_multiple_markdown_tutorials_should_be_easy_to_setup() {

        // Load all markdown files from docs/docs/tutorials directory
        Path tutorialsPath = Paths.get("docs/docs/tutorials");
        PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:*.md");
        List<Document> documents = FileSystemDocumentLoader.loadDocuments(tutorialsPath, pathMatcher);

        // Verify documents were loaded
        assertThat(documents).hasSizeGreaterThan(20); // When I write this, there are 26 .md files

        // Create embedding store
        EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

        // Ingest all documents with MarkdownSectionSplitter
        EmbeddingStoreIngestor.builder()
                .documentSplitter(MarkdownSectionSplitter.builder().build())
                .embeddingStore(embeddingStore)
                .build()
                .ingest(documents);

        // Create RAG-enabled assistant
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .contentRetriever(EmbeddingStoreContentRetriever.from(embeddingStore))
                .build();

        // Test that it can answer questions from the tutorials
        String answer = assistant.chat("What metadata does MarkdownSectionSplitter add to segments?");

        // Verify the answer contains relevant information from chunking.md
        assertThat(answer).containsAnyOf("md_section_level", "md_section_header", "metadata");

        answer = assistant.chat("How can I log each request and response sent to the LLM using Spring Boot?");

        // Verify the answer contains relevant information from logging.md
        assertThat(answer).containsAnyOf("langchain4j.open-ai.chat-model.log-requests=true");
    }

    private Path toPath(String fileName) {
        try {
            return Paths.get(getClass().getClassLoader().getResource(fileName).toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
