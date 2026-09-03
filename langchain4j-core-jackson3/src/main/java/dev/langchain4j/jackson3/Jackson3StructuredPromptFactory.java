package dev.langchain4j.jackson3;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;
import static com.fasterxml.jackson.annotation.PropertyAccessor.FIELD;

import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.input.structured.StructuredPrompt;
import dev.langchain4j.spi.prompt.structured.StructuredPromptFactory;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Jackson 3 twin of the default structured prompt factory.
 */
public class Jackson3StructuredPromptFactory implements StructuredPromptFactory {

    private static final ObjectMapper OBJECT_MAPPER = Jackson3Defaults.pinJackson2Defaults(JsonMapper.builder())
            .changeDefaultVisibility(vc -> vc.withVisibility(FIELD, ANY))
            .build();

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    @Override
    public Prompt toPrompt(Object structuredPrompt) {
        StructuredPrompt annotation = StructuredPrompt.Util.validateStructuredPrompt(structuredPrompt);
        PromptTemplate promptTemplate = PromptTemplate.from(StructuredPrompt.Util.join(annotation));
        return promptTemplate.apply(extractVariables(structuredPrompt));
    }

    private static Map<String, Object> extractVariables(Object structuredPrompt) {
        return OBJECT_MAPPER.readValue(OBJECT_MAPPER.writeValueAsString(structuredPrompt), MAP_TYPE);
    }
}
