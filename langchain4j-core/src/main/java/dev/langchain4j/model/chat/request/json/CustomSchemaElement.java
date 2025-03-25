package dev.langchain4j.model.chat.request.json;

import java.lang.reflect.Type;
import java.util.Map;

public interface CustomSchemaElement extends JsonSchemaElement {

    Map<String, Object> toMap();

    Object coerceArgument(Object argument, String parameterName, Class<?> parameterClass, Type parameterType);
}
