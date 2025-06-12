package dev.langchain4j.service.output;

import dev.langchain4j.Internal;

@Internal
interface OutputParserFactory {

    OutputParser<?> get(Class<?> rawClass, Class<?> typeArgumentClass);
}
