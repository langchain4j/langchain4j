package dev.langchain4j.model.output;

import java.util.Set;

import static dev.langchain4j.internal.Utils.setOf;

public class BooleanOutputParser implements TextOutputParser<Boolean> {

    @Override
    public Boolean parse(String string) {
        return Boolean.parseBoolean(string);
    }

    @Override
    public Set<Class<?>> getSupportedTypes() {
        return setOf(Boolean.class, boolean.class);
    }

    @Override
    public String formatInstructions() {
        return "one of [true, false]";
    }
}
