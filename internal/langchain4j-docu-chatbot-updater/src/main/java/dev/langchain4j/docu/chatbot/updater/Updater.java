package dev.langchain4j.docu.chatbot.updater;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.List;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.collection.DropCollectionParam;
import io.milvus.param.collection.RenameCollectionParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Updater {

    private static final Logger log = LoggerFactory.getLogger(Updater.class);

    private static final String COLLECTION_NAME = "docu"; // chatbot app expects this name
    private static final String COLLECTION_NAME_OLD = "docu_old";

    public static void main(String[] args) {

        MilvusServiceClient milvusServiceClient = new MilvusServiceClient(ConnectParam.newBuilder()
                .withUri(System.getenv("MILVUS_URI"))
                .withToken(System.getenv("MILVUS_API_KEY"))
                .build());

        Path currentPath = Paths.get("").toAbsolutePath();
        log.info("current path: " + currentPath.toString());
        Path docsPath = currentPath.resolve("docs/docs");
        log.info("docs path: " + docsPath.toString());

        PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:**.{md,mdx}");
        List<Document> documents = FileSystemDocumentLoader.loadDocumentsRecursively(docsPath, pathMatcher);

        DocumentSplitter documentSplitter = DocumentSplitters.recursive(
                600,
                150,
                new OpenAiTokenCountEstimator("gpt-5")
        );

        EmbeddingModel embeddingModel = GoogleAiEmbeddingModel.builder()
                .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
                .modelName("text-embedding-004") // chatbot app expects this embedding model
                .build();

        log.info("about to rename collection '" + COLLECTION_NAME + "' into '" + COLLECTION_NAME_OLD + "'");
        milvusServiceClient.renameCollection(RenameCollectionParam.newBuilder()
                .withOldCollectionName(COLLECTION_NAME)
                .withNewCollectionName(COLLECTION_NAME_OLD)
                .build());
        log.info("renamed collection '" + COLLECTION_NAME + "' into '" + COLLECTION_NAME_OLD + "'");

        EmbeddingStore<TextSegment> embeddingStore = MilvusEmbeddingStore.builder()
                .uri(System.getenv("MILVUS_URI"))
                .token(System.getenv("MILVUS_API_KEY"))
                .collectionName(COLLECTION_NAME)
                .dimension(embeddingModel.dimension())
                .build();

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentTransformer(document -> {
                    String absoluteDirectoryPath = document.metadata().getString("absolute_directory_path");
                    String relativeDirectoryPath = absoluteDirectoryPath.replaceFirst(".*?docs[\\\\/]docs[\\\\/]?", "");
                    document.metadata().put("relative_directory_path", relativeDirectoryPath);
                    document.metadata().remove("absolute_directory_path");
                    return document;
                })
                .documentSplitter(documentSplitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        try {
            ingestor.ingest(documents);

            log.info("about to remove collection '" + COLLECTION_NAME_OLD + "'");
            milvusServiceClient.dropCollection(DropCollectionParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME_OLD)
                    .build());
            log.info("removed collection '" + COLLECTION_NAME_OLD + "'");

        } catch (Exception e) {

            log.info("Failed to ingest", e);

            // rollback
            log.info("rollback: about to rename collection '" + COLLECTION_NAME_OLD + "' into '" + COLLECTION_NAME + "'");
            milvusServiceClient.renameCollection(RenameCollectionParam.newBuilder()
                    .withOldCollectionName(COLLECTION_NAME_OLD)
                    .withNewCollectionName(COLLECTION_NAME)
                    .build());
            log.info("rollback: renamed collection '" + COLLECTION_NAME_OLD + "' into '" + COLLECTION_NAME + "'");
        }
    }
}
