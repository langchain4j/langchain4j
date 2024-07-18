package dev.langchain4j.service;

import dev.langchain4j.model.output.OutputParser;

import java.util.Optional;

public interface OutputParserFactory {
    Optional<OutputParser<?>> get(Class<?> rawClass, Class<?> typeArgumentClass);
}
