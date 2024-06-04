package dev.langchain4j.model.output;

import java.util.Set;

import static dev.langchain4j.internal.Utils.setOf;

public class ShortOutputParser implements TextOutputParser<Short> {

    @Override
    public Set<Class<?>> getSupportedTypes() {
        return setOf(Short.class, short.class);
    }

    @Override
    public Short parse(String string) {
        return Short.parseShort(string);
    }

    @Override
    public String formatInstructions() {
        return "integer number in range [-32768, 32767]";
    }
}
