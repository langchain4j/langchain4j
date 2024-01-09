package dev.langchain4j.spi.prompt;

import java.util.Map;

public interface PromptTemplateFactory {

    Template create(Input input);

    interface Input {

        String getTemplate();

        default String getName() { return "template"; }
    }

    interface Template {

        String render(Map<String, Object> variables);
    }
}
