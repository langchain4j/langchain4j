package dev.langchain4j.agentic.internal;

public interface A2AClientBuilder<T> {

    A2AClientBuilder<T> inputNames(String... inputNames);
    A2AClientBuilder<T> outputName(String outputName);

    T build();
}
