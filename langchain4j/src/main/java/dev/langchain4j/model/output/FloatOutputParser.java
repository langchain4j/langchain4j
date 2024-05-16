package dev.langchain4j.model.output;

import java.util.Set;

import static dev.langchain4j.internal.Utils.setOf;

public class FloatOutputParser implements TextOutputParser<Float> {

    @Override
    public Float parse(String string) {
        return Float.parseFloat(string);
    }

    @Override
    public Set<Class<?>> getSupportedTypes() {
        return setOf(Float.class, float.class);
    }

    @Override
    public String formatInstructions() {
        return "floating point number";
    }
}
