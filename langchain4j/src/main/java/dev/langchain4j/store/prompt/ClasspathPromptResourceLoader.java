package dev.langchain4j.store.prompt;

import dev.langchain4j.spi.services.prompt.PromptResourceLoader;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * Built-in loader for classpath resources.
 * <p>
 * Uses {@link PromptResourceLoader#CLASSPATH_PRIORITY} (0) as its priority.
 * Custom loaders can override classpath resources by using a higher priority,
 * or provide fallback behavior by using a lower priority.
 */
public class ClasspathPromptResourceLoader implements PromptResourceLoader {

    @Override
    public String getProtocol() {
        // Returns null to handle resources without a protocol prefix
        return null;
    }

    @Override
    public String loadResource(String resource, Class<?> contextClass) {
        InputStream inputStream = contextClass.getResourceAsStream(resource);
        if (inputStream == null) {
            inputStream = contextClass.getResourceAsStream("/" + resource);
        }
        return getText(inputStream);
    }

    @Override
    public int getPriority() {
        return CLASSPATH_PRIORITY;
    }

    private String getText(InputStream inputStream) {
        if (inputStream == null) {
            return null;
        }
        try (Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8).useDelimiter("\\A")) {
            return scanner.hasNext() ? scanner.next() : "";
        }
    }
}
