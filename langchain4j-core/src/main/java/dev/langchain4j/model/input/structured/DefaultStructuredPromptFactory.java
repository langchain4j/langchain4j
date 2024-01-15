package dev.langchain4j.model.input.structured;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;
import com.google.gson.reflect.TypeToken;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.spi.prompt.structured.StructuredPromptFactory;
import java.util.Map;

public class DefaultStructuredPromptFactory implements StructuredPromptFactory {
    private static final Gson GSON = new GsonBuilder().setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE).create();

    @Override
    public Prompt toPrompt(Object structuredPrompt) {
        StructuredPrompt annotation = StructuredPrompt.Util.validateStructuredPrompt(structuredPrompt);

        String promptTemplateString = String.join(annotation.delimiter(), annotation.value());
        PromptTemplate promptTemplate = PromptTemplate.from(promptTemplateString);

        Map<String, Object> variables = extractVariables(structuredPrompt);

        return promptTemplate.apply(variables);
    }

    private static Map<String, Object> extractVariables(Object structuredPrompt) {
        String json = GSON.toJson(structuredPrompt);
        TypeToken<Map<String, Object>> mapType = new TypeToken<Map<String, Object>>() {};
        return GSON.fromJson(json, mapType);
    }
}
