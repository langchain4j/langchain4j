package dev.langchain4j.jackson3;

import static dev.langchain4j.spi.PrioritizedFactory.YIELDS_TO_OTHERS;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;
import static com.fasterxml.jackson.annotation.PropertyAccessor.FIELD;

import dev.langchain4j.exception.JsonReadException;
import dev.langchain4j.exception.JsonWriteException;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.input.structured.StructuredPrompt;
import dev.langchain4j.spi.PrioritizedFactory;
import dev.langchain4j.spi.prompt.structured.StructuredPromptFactory;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
/**
 * Jackson 3 twin of the default structured prompt factory.
 */
public class Jackson3StructuredPromptFactory implements StructuredPromptFactory, PrioritizedFactory {

    @Override
    public int priority() {
        return YIELDS_TO_OTHERS; // a framework that supplies its own codec keeps it
    }


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
        String json;
        try {
            json = OBJECT_MAPPER.writeValueAsString(structuredPrompt);
        } catch (JacksonException e) {
            throw new JsonWriteException(e);
        }
        try {
            return OBJECT_MAPPER.readValue(json, MAP_TYPE);
        } catch (JacksonException e) {
            throw new JsonReadException(e);
        }
    }
}
