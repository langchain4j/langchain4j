package dev.langchain4j.agentic.scope;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.Internal;

@Internal
public interface ConfigurableAgenticScopeJsonCodec extends AgenticScopeJsonCodec {
    void allowDeserializationPackagePrefix(String packagePrefix);

    void allowDeserializationType(Class<?> type);

    void withClassLoader(ClassLoader classloader);

    void registerForDeserializationPackageOf(Class<?> type);

    ObjectMapper objectMapper();
}
