package dev.langchain4j.model.output;

import java.util.Set;

import static dev.langchain4j.internal.Utils.setOf;

/**
 * Parser that simply returns the text as-is.
 */
public class StringOutputParser implements TextOutputParser<String> {
    @Override
    public Set<Class<?>> getSupportedTypes() {
        return setOf(String.class);
    }

    @Override
    public String parse(String text) {
        return text;
    }

    @Override
    public String formatInstructions() {
        return null;
    }
}
