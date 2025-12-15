package dev.langchain4j.agent.tool;

import dev.langchain4j.internal.Json;
import dev.langchain4j.invocation.LangChain4jManaged;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.internal.JsonSchemaElementUtils.VisitedClassMetadata;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.internal.JsonSchemaElementUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

/**
 * Utility methods for {@link ToolSpecification}s.
 */
public class ToolSpecifications {

    private static final Type MAP_TYPE = new ParameterizedType() {

        @Override
        public Type[] getActualTypeArguments() {
            return new Type[] {String.class, Object.class};
        }

        @Override
        public Type getRawType() {
            return Map.class;
        }

        @Override
        public Type getOwnerType() {
            return null;
        }
    };

    private ToolSpecifications() {
    }

    /**
     * Returns {@link ToolSpecification}s for all methods annotated with @{@link Tool} within the specified class.
     *
     * @param classWithTools the class.
     * @return the {@link ToolSpecification}s.
     */
    public static List<ToolSpecification> toolSpecificationsFrom(Class<?> classWithTools) {
        List<ToolSpecification> toolSpecifications = stream(classWithTools.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .map(ToolSpecifications::toolSpecificationFrom)
                .collect(toList());
        validateSpecifications(toolSpecifications);
        return toolSpecifications;
    }

    /**
     * Returns {@link ToolSpecification}s for all methods annotated with @{@link Tool}
     * within the class of the specified object.
     *
     * @param objectWithTools the object.
     * @return the {@link ToolSpecification}s.
     */
    public static List<ToolSpecification> toolSpecificationsFrom(Object objectWithTools) {
        return toolSpecificationsFrom(objectWithTools.getClass());
    }

    /**
     * Validates all the {@link ToolSpecification}s. The validation checks for duplicate method names.
     * Throws {@link IllegalArgumentException} if validation fails
     *
     * @param toolSpecifications list of ToolSpecification to be validated.
     */
    public static void validateSpecifications(List<ToolSpecification> toolSpecifications) throws IllegalArgumentException {

        // Checks for duplicates methods
        Set<String> names = new HashSet<>();
        for (ToolSpecification toolSpecification : toolSpecifications) {
            if (!names.add(toolSpecification.name())) {
                throw new IllegalArgumentException(String.format("Tool names must be unique. The tool '%s' appears several times", toolSpecification.name()));
            }
        }
    }

    /**
     * Returns the {@link ToolSpecification} for the given method annotated with @{@link Tool}.
     *
     * @param method the method.
     * @return the {@link ToolSpecification}.
     */
    public static ToolSpecification toolSpecificationFrom(Method method) {
        Tool tool = method.getAnnotation(Tool.class);
        return ToolSpecification.builder()
                .name(getName(tool, method))
                .description(getDescription(tool))
                .parameters(parametersFrom(method.getParameters()))
                .metadata(getMetadata(tool))
                .build();
    }

    private static String getName(Tool tool, Method method) {
        return isNullOrBlank(tool.name()) ? method.getName() : tool.name();
    }

    private static String getDescription(Tool tool) {
        String description = String.join("\n", tool.value());
        return description.isEmpty() ? null : description;
    }

    private static Map<String, Object> getMetadata(Tool annotation) {
        return Json.fromJson(annotation.metadata(), MAP_TYPE);
    }

    private static JsonObjectSchema parametersFrom(Parameter[] parameters) {

        Map<String, JsonSchemaElement> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        Map<Class<?>, VisitedClassMetadata> visited = new LinkedHashMap<>();

        for (Parameter parameter : parameters) {
            if (parameter.isAnnotationPresent(ToolMemoryId.class)
                    || InvocationParameters.class.isAssignableFrom(parameter.getType())
                    || LangChain4jManaged.class.isAssignableFrom(parameter.getType())
                    || parameter.getType() == InvocationContext.class) {
                continue;
            }

            boolean isRequired = Optional.ofNullable(parameter.getAnnotation(P.class))
                    .map(P::required)
                    .orElse(true);

            properties.put(parameter.getName(), jsonSchemaElementFrom(parameter, visited));
            if (isRequired) {
                required.add(parameter.getName());
            }
        }

        Map<String, JsonSchemaElement> definitions = new LinkedHashMap<>();
        visited.forEach((clazz, visitedClassMetadata) -> {
            if (visitedClassMetadata.recursionDetected) {
                definitions.put(visitedClassMetadata.reference, visitedClassMetadata.jsonSchemaElement);
            }
        });

        if (properties.isEmpty()) {
            return null;
        }

        return JsonObjectSchema.builder()
                .addProperties(properties)
                .required(required)
                .definitions(definitions.isEmpty() ? null : definitions)
                .build();
    }

    private static JsonSchemaElement jsonSchemaElementFrom(Parameter parameter,
                                                           Map<Class<?>, VisitedClassMetadata> visited) {
        P annotation = parameter.getAnnotation(P.class);
        String description = annotation == null ? null : annotation.value();
        return JsonSchemaElementUtils.jsonSchemaElementFrom(
                parameter.getType(),
                parameter.getParameterizedType(),
                description,
                true,
                visited
        );
    }
}
