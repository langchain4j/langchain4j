package dev.langchain4j.model.output;

import java.util.Set;

import static dev.langchain4j.internal.Utils.setOf;

public class IntOutputParser implements TextOutputParser<Integer> {

    @Override
    public Set<Class<?>> getSupportedTypes() {
        return setOf(Integer.class, int.class);
    }

    @Override
    public Integer parse(String string) {
        return Integer.parseInt(string);
    }

    @Override
    public String formatInstructions() {
        return "integer number";
    }
}
