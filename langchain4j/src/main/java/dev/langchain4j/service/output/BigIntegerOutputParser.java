package dev.langchain4j.service.output;

import static dev.langchain4j.service.output.ParsingUtils.parseAsStringOrJson;

import dev.langchain4j.Internal;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import java.math.BigInteger;
import java.util.Optional;

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
    public Optional<JsonSchema> jsonSchema() {
        JsonSchema jsonSchema = JsonSchema.builder()
                .name("integer")
                .rootElement(JsonObjectSchema.builder()
                        .addIntegerProperty("value")
                        .required("value")
                        .build())
                .build();
        return Optional.of(jsonSchema);
    }

    @Override
    public String formatInstructions() {
        return "integer number";
    }
}
