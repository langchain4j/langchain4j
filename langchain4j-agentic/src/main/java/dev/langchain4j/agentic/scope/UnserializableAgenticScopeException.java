package dev.langchain4j.agentic.scope;

import dev.langchain4j.exception.LangChain4jException;

public class UnserializableAgenticScopeException extends LangChain4jException {

    public UnserializableAgenticScopeException(String className, Exception cause) {
        super("Failed to deserialize AgenticScope from JSON. " +
                "The type '" + className + "' is not allowed for deserialization. " +
                "To fix this, register the type before deserialization occurs by calling: " +
                "AgenticScopeSerializer.allowDeserializationType(" + className + ".class) " +
                "or register its package prefix: " +
                "AgenticScopeSerializer.allowDeserializationPackagePrefix(\"" + toPackagePrefix(className) + "\")",
                cause);
    }

    private static String toPackagePrefix(String className) {
        int lastDot = className.lastIndexOf('.');
        return lastDot > 0 ? className.substring(0, lastDot + 1) : className + ".";
    }
}
