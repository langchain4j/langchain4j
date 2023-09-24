package dev.langchain4j.model.input.structured;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;
import com.google.gson.reflect.TypeToken;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import java.util.Map;

public class StructuredPromptProcessor {

  private static final Gson GSON = new GsonBuilder().setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE).create();

  public static Prompt toPrompt(Object structuredPrompt) {
    validate(structuredPrompt);

    StructuredPrompt annotation = structuredPrompt.getClass().getAnnotation(StructuredPrompt.class);

    String promptTemplateString = String.join(annotation.delimiter(), annotation.value());
    PromptTemplate promptTemplate = PromptTemplate.from(promptTemplateString);

    Map<String, Object> variables = extractVariables(structuredPrompt);

    return promptTemplate.apply(variables);
  }

  private static void validate(Object structuredPrompt) {
    if (structuredPrompt == null) {
      throw new IllegalArgumentException("Structured prompt cannot be null");
    }

    String structuredPromptClassName = structuredPrompt.getClass().getName();

    StructuredPrompt structuredPromptAnnotation = structuredPrompt.getClass().getAnnotation(StructuredPrompt.class);
    if (structuredPromptAnnotation == null) {
      throw new IllegalArgumentException(
        String.format(
          "%s should be annotated with @StructuredPrompt to be used as a structured prompt",
          structuredPromptClassName
        )
      );
    }
  }

  private static Map<String, Object> extractVariables(Object structuredPrompt) {
    String json = GSON.toJson(structuredPrompt);
    TypeToken<Map<String, Object>> mapType = new TypeToken<Map<String, Object>>() {};
    return GSON.fromJson(json, mapType);
  }
}
