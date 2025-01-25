package dev.langchain4j.store.embedding.pinecone;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

class PineconeContainer extends GenericContainer<PineconeContainer> {

    public PineconeContainer() {
        super(DockerImageName.parse("ghcr.io/pinecone-io/pinecone-local:latest"));
        withEnv("HTTP_ENABLED", String.valueOf(true));
        withEnv("HTTP_PORT", String.valueOf(5080));
        this.withExposedPorts(5080, 5081)
                .withEnv("PORT", "5080")
                .withEnv("PINECONE_HOST", "localhost");
    }


    public int getHttpPort() {
        return this.getMappedPort(5080);
    }
}
