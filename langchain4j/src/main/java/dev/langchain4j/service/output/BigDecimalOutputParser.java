package dev.langchain4j.service.output;

import static dev.langchain4j.service.output.ParsingUtils.parseAsStringOrJson;

import dev.langchain4j.Internal;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import java.math.BigDecimal;
import java.util.Optional;

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
    public Optional<JsonSchema> jsonSchema() {
        JsonSchema jsonSchema = JsonSchema.builder()
                .name("number")
                .rootElement(JsonObjectSchema.builder()
                        .addNumberProperty("value")
                        .required("value")
                        .build())
                .build();
        return Optional.of(jsonSchema);
    }

    @Override
    public String formatInstructions() {
        return "floating point number";
    }
}
