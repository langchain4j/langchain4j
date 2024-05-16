package dev.langchain4j.model.output;

import java.util.Set;

import static dev.langchain4j.internal.Utils.setOf;

public class ByteOutputParser implements TextOutputParser<Byte> {

    @Override
    public Set<Class<?>> getSupportedTypes() {
        return setOf(Byte.class, byte.class);
    }

    @Override
    public Byte parse(String string) {
        return Byte.parseByte(string);
    }

    @Override
    public String formatInstructions() {
        return "integer number in range [-128, 127]";
    }
}
