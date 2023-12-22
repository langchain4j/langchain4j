package dev.langchain4j.spi.prompt;

import java.util.Map;

public interface PromptTemplateFactory {

    Template create(Input input);

    interface Input {

        String getTemplate();

        String getName();
    }

    interface Template {

        String render(Map<String, Object> variables);
    }
}
