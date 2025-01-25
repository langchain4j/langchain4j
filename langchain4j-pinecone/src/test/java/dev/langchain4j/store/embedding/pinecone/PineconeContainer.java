package dev.langchain4j.store.embedding.pinecone;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.pinecone.clients.DockerPinecone;
import io.pinecone.clients.Index;
import io.pinecone.clients.Pinecone;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openapitools.db_control.client.model.DeletionProtection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.util.Arrays;

class PineconeContainer extends GenericContainer<PineconeContainer> {

    public PineconeContainer() {
        super(DockerImageName.parse("ghcr.io/pinecone-io/pinecone-local:latest"));
        withEnv("HTTP_ENABLED", String.valueOf(true));
        withEnv("HTTP_PORT", String.valueOf(5080));
        this.withExposedPorts(5080, 5081)
                .withEnv("PORT", "5080")
                .withEnv("PINECONE_HOST", "localhost");
    }

    @Override
    public void start() {
        super.start(); // Start the container

        // Get the dynamically mapped HTTP port
        int httpPort = getHttpPort();

        // Update the service with the correct host and port after the container starts
        try {
            execInContainer(
                    "sh", "-c",
                    "export PINECONE_HOST=localhost:" + httpPort
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }



    public int getHttpPort() {
        return this.getMappedPort(5080);
    }


    private static PineconeContainer pineconeContainer;
    private static Pinecone pineconeClient;

    @BeforeAll
    static void setup() throws Exception {
        pineconeContainer = new PineconeContainer();
        pineconeContainer.start();


        String pineconeHost = "http://" + pineconeContainer.getHost() + ":" + pineconeContainer.getHttpPort();

        Integer indexPort = pineconeContainer.getMappedPort(5081);

        pineconeClient = new DockerPinecone(new Pinecone.Builder("pclocal")
                .withHost(pineconeHost)
                .withTlsEnabled(false)
                .build(), indexPort);
    }

    @Test
    void testPineconeLocalUpsertAndQuery() {

        String indexName = "pine-test";

        pineconeClient.createServerlessIndex(indexName, "cosine", 2, "aws", "us-east-1", DeletionProtection.DISABLED);

        // Get index connection object
        Index indexConnection = pineconeClient.getIndexConnection(indexName);

        // Upsert records into index
        Struct metaData1 = Struct.newBuilder()
                .putFields("genre", Value.newBuilder().setStringValue("drama").build())
                .build();
        Struct metaData2 = Struct.newBuilder()
                .putFields("genre", Value.newBuilder().setStringValue("documentary").build())
                .build();
        Struct metaData3 = Struct.newBuilder()
                .putFields("genre", Value.newBuilder().setStringValue("documentary").build())
                .build();

        indexConnection.upsert("vec1", Arrays.asList(1.0f, -2.5f),  null, null, metaData1, "example-namespace");
        indexConnection.upsert("vec2", Arrays.asList(3.0f, -2.0f),  null, null, metaData2, "example-namespace");
        indexConnection.upsert("vec3", Arrays.asList(0.5f, -1.5f),  null, null, metaData3, "example-namespace");

        // Query the index
        var response = indexConnection.query(1, Arrays.asList(1.0f, 1.5f), null, null, null, "example-namespace", null, true, false);
        System.out.println(response);
    }

    @AfterAll
    static void teardown() {
        if (pineconeContainer != null) {
            pineconeContainer.stop();
        }
    }

}
