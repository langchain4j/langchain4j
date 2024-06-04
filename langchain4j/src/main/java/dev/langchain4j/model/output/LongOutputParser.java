package dev.langchain4j.model.output;

import java.util.Set;

import static dev.langchain4j.internal.Utils.setOf;

public class LongOutputParser implements TextOutputParser<Long> {

    @Override
    public Set<Class<?>> getSupportedTypes() {
        return setOf(Long.class, long.class);
    }

    @Override
    public Long parse(String string) {
        return Long.parseLong(string);
    }

    @Override
    public String formatInstructions() {
        return "integer number";
    }
}
