package dev.langchain4j.service.output;

import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;

import java.util.Optional;

import static dev.langchain4j.service.output.ParsingUtils.parseAsValueOrJson;
import static dev.langchain4j.service.tool.DefaultToolExecutor.getBoundedLongValue;

class IntOutputParser implements OutputParser<Integer> {

    @Override
    public Integer parse(String text) {
        return parseAsValueOrJson(text, IntOutputParser::parseInt, Integer.class);
    }

    private static Integer parseInt(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException nfe) {
            return (int) getBoundedLongValue(text, "int", Integer.class, Integer.MIN_VALUE, Integer.MAX_VALUE);
        }
    }

    @Override
    public Optional<JsonSchema> jsonSchema() {
        JsonSchema jsonSchema = JsonSchema.builder()
                .name("integer") // TODO add range? description?
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
