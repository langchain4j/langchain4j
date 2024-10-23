package dev.langchain4j.service.output;

import java.util.Optional;

interface OutputParserFactory {

    Optional<OutputParser<?>> get(Class<?> rawClass, Class<?> typeArgumentClass);
}
