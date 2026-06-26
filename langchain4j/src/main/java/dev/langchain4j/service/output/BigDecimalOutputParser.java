package dev.langchain4j.service.output;

import static dev.langchain4j.service.output.ParsingUtils.parseAsStringOrJson;

import dev.langchain4j.Internal;
import java.math.BigDecimal;

@Internal
class BigDecimalOutputParser implements OutputParser<BigDecimal> {

    @Override
    public BigDecimal parse(String text) {
        return parseAsStringOrJson(text, BigDecimalOutputParser::parseBigDecimal, BigDecimal.class);
    }

    private static BigDecimal parseBigDecimal(String text) {
        return new BigDecimal(text.trim());
    }

    @Override
    public String formatInstructions() {
        return "floating point number";
    }
}
