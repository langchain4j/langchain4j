package dev.langchain4j.agent.tool;

import static dev.langchain4j.internal.Utils.allConcreteMethods;
import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.JsonSchemaElementUtils;
import dev.langchain4j.internal.JsonSchemaElementUtils.VisitedClassMetadata;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.invocation.LangChain4jManaged;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import java.lang.reflect.Executable;
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
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static dev.langchain4j.agent.tool.SearchBehavior.SEARCHABLE;
import static dev.langchain4j.agent.tool.ToolSpecification.METADATA_SEARCH_BEHAVIOR;

/**
 * Utility methods for {@link ToolSpecification}s.
 */
public class ToolSpecifications {

    private static final Logger log = LoggerFactory.getLogger(ToolSpecifications.class);

    /**
     * Per-class flag, so repeated spec generation neither allocates nor keeps classes alive:
     * {@link ClassValue} storage is attached to the class itself and dies with its class loader.
     */
    private static final ClassValue<AtomicBoolean> ALREADY_WARNED_ABOUT = new ClassValue<>() {

        @Override
        protected AtomicBoolean computeValue(Class<?> type) {
            return new AtomicBoolean();
        }
    };

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

    private ToolSpecifications() {}

    /**
     * Returns {@link ToolSpecification}s for all methods annotated with @{@link Tool} within the specified class.
     *
     * @param classWithTools the class.
     * @return the {@link ToolSpecification}s.
     */
    public static List<ToolSpecification> toolSpecificationsFrom(Class<?> classWithTools) {
        List<ToolSpecification> toolSpecifications = allConcreteMethods(classWithTools).stream()
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
    public static void validateSpecifications(List<ToolSpecification> toolSpecifications)
            throws IllegalArgumentException {

        // Checks for duplicates methods
        Set<String> names = new HashSet<>();
        for (ToolSpecification toolSpecification : toolSpecifications) {
            if (!names.add(toolSpecification.name())) {
                throw new IllegalArgumentException(String.format(
                        "Tool names must be unique. The tool '%s' appears several times", toolSpecification.name()));
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
        Map<String, Object> metadata = Json.fromJson(annotation.metadata(), MAP_TYPE);
        if (annotation.searchBehavior() != SEARCHABLE) {
            metadata.put(METADATA_SEARCH_BEHAVIOR, annotation.searchBehavior());
        }
        return metadata;
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

            boolean isOptional = Optional.class.equals(parameter.getType());
            P pAnnotation = parameter.getAnnotation(P.class);
            boolean hasDefaultValue =
                    pAnnotation != null && !P.NO_DEFAULT.equals(pAnnotation.defaultValue());
            boolean isRequired = !isOptional
                    && !hasDefaultValue
                    && Optional.ofNullable(pAnnotation)
                            .map(P::required)
                            .orElse(true);

            String parameterName = Optional.ofNullable(pAnnotation)
                    .map(P::name)
                    .filter(name -> isNotNullOrBlank(name))
                    .orElseGet(() -> {
                        String warning = unavailableParameterNameWarning(parameter);
                        if (warning != null) {
                            log.warn(warning);
                        }
                        return parameter.getName();
                    });

            properties.put(parameterName, jsonSchemaElementFrom(parameter, visited));
            if (isRequired) {
                required.add(parameterName);
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

    /**
     * Returns the warning to log when a tool parameter's name is unavailable at runtime, or {@code null}
     * when it is available. Names are unavailable when the class was compiled without the
     * {@code -parameters} javac flag; the LLM then sees {@code arg0} instead of a meaningful name,
     * which degrades tool calling accuracy.
     * <p>
     * Returns the warning at most once per declaring class, since the flag is a property of how that
     * class was compiled rather than of any single parameter. When names are available - the case that
     * matters for throughput - this reads one boolean and allocates nothing.
     */
    static String unavailableParameterNameWarning(Parameter parameter) {
        if (parameter.isNamePresent()) {
            return null;
        }
        Executable method = parameter.getDeclaringExecutable();
        if (ALREADY_WARNED_ABOUT.get(method.getDeclaringClass()).getAndSet(true)) {
            return null;
        }
        return ("Parameter '%s' of tool method '%s.%s' has no name available at runtime, so the LLM will "
                        + "see it as '%s'. Meaningless parameter names degrade tool calling accuracy. "
                        + "Either compile with the '-parameters' javac flag "
                        + "(<maven.compiler.parameters>true</maven.compiler.parameters>, or "
                        + "kotlinOptions.javaParameters=true), "
                        + "or name the parameter explicitly with @P(name = \"...\").")
                .formatted(
                        parameter.getName(),
                        method.getDeclaringClass().getName(),
                        method.getName(),
                        parameter.getName());
    }

    private static JsonSchemaElement jsonSchemaElementFrom(
            Parameter parameter, Map<Class<?>, VisitedClassMetadata> visited) {
        P annotation = parameter.getAnnotation(P.class);
        String description = null;

        if (annotation != null) {
            if (isNotNullOrBlank(annotation.value()) && isNotNullOrBlank(annotation.description())) {
                throw new IllegalArgumentException(String.format(
                        "Parameter '%s' has both 'value' and 'description' set in @P. Use one or the other, but not both.",
                        parameter.getName()));
            }
            if (isNotNullOrBlank(annotation.description())) {
                description = annotation.description();
            } else if (isNotNullOrBlank(annotation.value())) {
                description = annotation.value();
            }
        }

        Type type = parameter.getParameterizedType();
        Class<?> clazz = parameter.getType();

        if (clazz == Optional.class && type instanceof ParameterizedType parameterizedType) {
            // Use the variable 'parameterizedType' directly without casting
            type = parameterizedType.getActualTypeArguments()[0];

            if (type instanceof Class) {
                clazz = (Class<?>) type;
            } else if (type instanceof ParameterizedType parameterizedType1) {
                clazz = (Class<?>) parameterizedType1.getRawType();
            }
        }

        return JsonSchemaElementUtils.jsonSchemaElementFrom(clazz, type, description, true, visited);
    }
}
