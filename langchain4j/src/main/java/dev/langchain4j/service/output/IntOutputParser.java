package dev.langchain4j.service.output;

import dev.langchain4j.internal.Json;

import java.util.Map;

import static dev.langchain4j.service.tool.DefaultToolExecutor.getBoundedLongValue;

class IntOutputParser implements OutputParser<Integer> {

    @Override
    public Integer parse(String text) {
        // Handle null or blank => throw NumberFormatException
        if (text == null || text.isBlank()) {
            throw new NumberFormatException("No data to parse");
        }

        String trimmed = text.trim();

        // Check if it looks like JSON
        if (trimmed.startsWith("{")) {
            Map<?, ?> map = Json.fromJson(trimmed, Map.class);

            if (map == null || map.isEmpty()) {
                throw new NumberFormatException("No data to parse");
            }

            // Check for "value" key or fallback to the first property
            Object value;
            if (map.containsKey("value")) {
                value = map.get("value");
            } else {
                value = map.values().iterator().next();
            }

            // If the value is null => throw
            if (value == null) {
                throw new NumberFormatException("No data to parse");
            }

            return parseToInt(value.toString().trim());
        }

        return parseToInt(trimmed);
    }

    private Integer parseToInt(String val) {
        try {
            // First try strict integer parsing
            return Integer.parseInt(val);
        } catch (NumberFormatException ex) {
            try {
                 return (int) getBoundedLongValue(val, "value of the int output parser", Integer.class, Integer.MIN_VALUE, Integer.MAX_VALUE);
            } catch (NumberFormatException nestedEx) {
                throw new NumberFormatException("Could not parse integer from: " + val);
            }
        }
    }

    @Override
    public String formatInstructions() {
        return "integer number";
    }
}
