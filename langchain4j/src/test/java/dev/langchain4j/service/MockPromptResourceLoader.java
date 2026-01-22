package dev.langchain4j.service;

import dev.langchain4j.spi.services.prompt.PromptResourceLoader;
import java.util.Map;

/**
 * Mock implementation of PromptResourceLoader for testing SPI-based prompt loading.
 * Provides predefined prompts accessible via "mock:" protocol.
 */
public class MockPromptResourceLoader implements PromptResourceLoader {

    private static final Map<String, String> MOCK_PROMPTS = Map.of(
            "mock:simple-recipe", "Create a simple recipe using {{it}}",
            "mock:detailed-recipe", "Create a detailed recipe with steps for {{it}}. Include preparation time.",
            "mock:with-system", "You are a {{character}} chef");

    @Override
    public String getProtocol() {
        return "mock";
    }

    @Override
    public String loadResource(String resource, Class<?> contextClass) throws Exception {
        String prompt = MOCK_PROMPTS.get(resource);
        if (prompt == null) {
            throw new IllegalArgumentException("Mock resource not found: " + resource);
        }
        return prompt;
    }

    @Override
    public int getPriority() {
        return 100;
    }
}
