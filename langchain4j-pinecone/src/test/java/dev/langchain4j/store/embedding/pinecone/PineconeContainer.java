package dev.langchain4j.store.embedding.pinecone;

import org.testcontainers.containers.GenericContainer;

public class PineconeContainer extends GenericContainer<PineconeContainer> {

    public PineconeContainer() {
        super("ghcr.io/pinecone-io/pinecone-index:latest");
    }

    @Override
    protected void configure() {
        withEnv("PORT", "5080");
        withEnv("INDEX_TYPE", "serverless");
        withEnv("DIMENSION", "2");
        withEnv("METRIC", "cosine");
        withExposedPorts(5080);
    }



}
