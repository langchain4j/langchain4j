package dev.langchain4j.model.output;

import java.util.Set;

import static dev.langchain4j.internal.Utils.setOf;

public class DoubleOutputParser implements TextOutputParser<Double> {

    @Override
    public Set<Class<?>> getSupportedTypes() {
        return setOf(Double.class, double.class);
    }

    @Override
    public Double parse(String string) {
        return Double.parseDouble(string);
    }

    @Override
    public String formatInstructions() {
        return "floating point number";
    }
}
