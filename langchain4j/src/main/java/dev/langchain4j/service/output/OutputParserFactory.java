package dev.langchain4j.service.output;

interface OutputParserFactory {

    OutputParser<?> get(Class<?> rawClass, Class<?> typeArgumentClass);
}
