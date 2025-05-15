package dev.langchain4j.service.output;

import dev.langchain4j.Internal;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;

import java.util.Optional;

import static dev.langchain4j.service.output.ParsingUtils.outputParsingException;
import static dev.langchain4j.service.output.ParsingUtils.parseAsStringOrJson;

@Internal
class BooleanOutputParser implements OutputParser<Boolean> {

    @Override
    public Boolean parse(String text) {
        return parseAsStringOrJson(text, BooleanOutputParser::parseBoolean, Boolean.class);
    }

    private static boolean parseBoolean(String text) {
        String trimmed = text.trim();
        if (trimmed.equalsIgnoreCase("true") || trimmed.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(trimmed);
        } else {
            throw outputParsingException(text, Boolean.class);
        }
    }

    @Override
    public Optional<JsonSchema> jsonSchema() {
        JsonSchema jsonSchema = JsonSchema.builder()
                .name("boolean")
                .rootElement(JsonObjectSchema.builder()
                        .addBooleanProperty("value")
                        .required("value")
                        .build())
                .build();
        return Optional.of(jsonSchema);
    }

    @Override
    public String formatInstructions() {
        return "one of [true, false]";
    }
}
