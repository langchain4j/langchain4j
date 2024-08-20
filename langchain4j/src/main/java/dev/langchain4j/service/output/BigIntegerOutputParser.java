package dev.langchain4j.service.output;

import java.math.BigInteger;

class BigIntegerOutputParser implements OutputParser<BigInteger> {

    @Override
    public BigInteger parse(String string) {
        return new BigInteger(string.trim());
    }

    @Override
    public String formatInstructions() {
        return "integer number";
    }
}
