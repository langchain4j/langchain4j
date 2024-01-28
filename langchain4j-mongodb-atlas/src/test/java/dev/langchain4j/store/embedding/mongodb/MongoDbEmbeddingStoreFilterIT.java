package dev.langchain4j.store.embedding.mongodb;

import com.mongodb.*;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;

import java.io.File;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class MongoDbEmbeddingStoreFilterIT {

    static final String MONGO_SERVICE_NAME = "mongo";
    static final Integer MONGO_SERVICE_PORT = 27778;
    static DockerComposeContainer<?> mongodb = new DockerComposeContainer<>(new File("src/test/resources/docker-compose.yml"))
            .withExposedService(MONGO_SERVICE_NAME, MONGO_SERVICE_PORT, new LogMessageWaitStrategy()
                    .withRegEx(".*Deployment created!.*\\n")
                    .withTimes(1)
                    .withStartupTimeout(Duration.ofMinutes(30)));

    static MongoClient client;

    EmbeddingStore<TextSegment> embeddingStore = MongoDbEmbeddingStore.builder()
            .fromClient(client)
            .databaseName("test_database")
            .collectionName("test_collection")
            .indexName("test_index")
            // TODO: filter test
            // .filter(Filters.and(Filters.in("")))
            .build();

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @BeforeAll
    @SneakyThrows
    static void start() {
        mongodb.start();

        MongoCredential credential = MongoCredential.createCredential("root", "admin", "root".toCharArray());
        client = MongoClients.create(
                MongoClientSettings.builder()
                        .credential(credential)
                        .serverApi(ServerApi.builder().version(ServerApiVersion.V1).build())
                        .applyConnectionString(new ConnectionString(String.format("mongodb://%s:%s/?directConnection=true",
                                mongodb.getServiceHost(MONGO_SERVICE_NAME, MONGO_SERVICE_PORT), mongodb.getServicePort(MONGO_SERVICE_NAME, MONGO_SERVICE_PORT))))
                        .build());
    }

    @AfterAll
    static void stop() {
        mongodb.stop();
        client.close();
    }

    @Test
    void should_find_relevant_with_filter() {
        // TODO
        assertThat(0).isEqualTo(0);
    }
}
