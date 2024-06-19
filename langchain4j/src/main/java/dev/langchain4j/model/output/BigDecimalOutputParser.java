package dev.langchain4j.model.output;

import java.math.BigDecimal;

public class BigDecimalOutputParser implements OutputParser<BigDecimal> {

    @Override
    public BigDecimal parse(String string) {
        return new BigDecimal(string.trim());
    }

    @Override
    public String formatInstructions() {
        return "floating point number";
    }
}
