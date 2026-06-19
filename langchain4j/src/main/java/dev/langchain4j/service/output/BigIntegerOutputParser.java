package dev.langchain4j.service.output;

import static dev.langchain4j.service.output.ParsingUtils.parseAsStringOrJson;

import dev.langchain4j.Internal;
import java.math.BigInteger;

@Internal
class BigIntegerOutputParser implements OutputParser<BigInteger> {

    @Override
    public BigInteger parse(String text) {
        return parseAsStringOrJson(text, BigIntegerOutputParser::parseBigInteger, BigInteger.class);
    }

    private static BigInteger parseBigInteger(String text) {
        return new BigInteger(text.trim());
    }

    @Override
    public String formatInstructions() {
        return "integer number";
    }
}
