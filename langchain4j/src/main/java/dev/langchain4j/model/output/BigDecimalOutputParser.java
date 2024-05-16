package dev.langchain4j.model.output;

import java.math.BigDecimal;
import java.util.Set;

import static dev.langchain4j.internal.Utils.setOf;

public class BigDecimalOutputParser implements TextOutputParser<BigDecimal> {
    @Override
    public Set<Class<?>> getSupportedTypes() {
        return setOf(BigDecimal.class);
    }

    @Override
    public BigDecimal parse(String string) {
        return new BigDecimal(string);
    }

    @Override
    public String formatInstructions() {
        return "floating point number";
    }
}
