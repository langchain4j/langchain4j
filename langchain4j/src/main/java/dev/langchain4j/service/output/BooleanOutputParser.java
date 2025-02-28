package dev.langchain4j.service.output;

import dev.langchain4j.internal.Json;

import java.util.Map;
class BooleanOutputParser implements OutputParser<Boolean> {

    @Override
    public Boolean parse(String text) {
        // 1. Check for null or blank
        if (text == null || text.isBlank()) {
            return false;
        }

        // 2. Check if it's JSON
        String trimmed = text.trim();
        if (trimmed.startsWith("{")) {

            // Attempt to parse JSON
            Map<?, ?> map = Json.fromJson(trimmed, Map.class);
            if (map == null || map.isEmpty()) {
                return false;
            }

            // 3. Retrieve the value. Use "value" if present; otherwise, fallback to the first property.
            Object value;
            if (map.containsKey("value")) {
                value = map.get("value");
            } else {
                value = map.values().iterator().next();  // fallback to first property
            }

            // If null, default to false
            if (value == null) {
                return false;
            }

            // 4. Convert to string, parse as boolean
            return Boolean.parseBoolean(value.toString().trim());
        }

        // 5. Plain text
        return Boolean.parseBoolean(trimmed);
    }

    @Override
    public String formatInstructions() {
        return "one of [true, false]";
    }
}
