package dev.langchain4j.service.output;

import dev.langchain4j.Internal;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;

import java.util.Optional;

import static dev.langchain4j.service.output.ParsingUtils.parseAsStringOrJson;
import static dev.langchain4j.service.tool.DefaultToolExecutor.getBoundedLongValue;

@Internal
class LongOutputParser implements OutputParser<Long> {

    @Override
    public Long parse(String text) {
        return parseAsStringOrJson(text, LongOutputParser::parseLong, Long.class);
    }

    private static Long parseLong(String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException nfe) {
            return getBoundedLongValue(text, "long", Long.class, Long.MIN_VALUE, Long.MAX_VALUE);
        }
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
