package dev.langchain4j.agent.tool;

import dev.langchain4j.Experimental;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.type;
import static dev.langchain4j.agent.tool.ToolSpecifications.toJsonSchemaProperties;
import static java.util.Arrays.asList;


/**
 * TODO
 */
@Builder
@Experimental
@AllArgsConstructor // TODO
public class ToolThingy<T> {
    // TODO name
    // TODO location

    private final String name;
    private final String description;
    private Map<String, ToolParameterThingy> parameters; // TODO name
    // TODO names/types/descriptions of arguments should probably also be dynamic?
    private final Class<T> argumentClass; // TODO needed? can get from function?
    private final ToolCallback<T> callback; // TODO name

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public Class<T> argumentClass() {
        return argumentClass;
    }

    public ToolCallback<T> callback() { // TODO name
        return callback;
    }

    // TODO move
    public ToolSpecification toToolSpecification() {

        ToolSpecification.Builder builder = ToolSpecification.builder()
                .name(name)
                .description(description);

        if (argumentClass == null) {
            builder.parameters(ToolParameters.builder().build()); // TODO needed?
        } else {
            for (Field field : argumentClass.getDeclaredFields()) {
                builder.addParameter(field.getName(), toJsonSchemaProperties(field));
            }
            // TODO validate conflicts or override?
            parameters.forEach((toolParameterName, toolParameterThingy) -> {
                List<JsonSchemaProperty> jsonSchemaProperties = asList(
                        type(toolParameterThingy.type()),
                        JsonSchemaProperty.description(toolParameterThingy.description())
                        // TODO other info?
                );
                if (toolParameterThingy.required()) {
                    builder.addParameter(toolParameterName, jsonSchemaProperties);
                } else {
                    builder.addOptionalParameter(toolParameterName, jsonSchemaProperties);
                }
                // TODO make sure no duplicates in "required" list
            });
        }

        return builder.build();
    }

    // TODO ctor? builder?
    // TODO test
    public static <T> ToolThingy<T> from(String name,
                                         String description,
                                         Map<String, ToolParameterThingy> parameters,
                                         Class<T> argumentClass,
                                         ToolCallback<T> callback) {
        return new ToolThingy<>(name, description, parameters, argumentClass, callback);
    }

    // TODO test
    public static <T> ToolThingy<T> from(String name,
                                         String description,
                                         Class<T> argumentClass,
                                         Function<T, ?> function) {
        return new ToolThingy<>(
                name,
                description,
                null,
                argumentClass,
                request -> {
                    T argument = request.argument();
                    Object result = function.apply(argument);
                    return new ToolResult(result.toString());
                }
        );
    }

    // TODO test
    public static ToolThingy<?> from(String name,
                                     String description,
                                     Supplier<?> supplier) {
        return new ToolThingy<>(
                name,
                description,
                null,
                null,
                request -> {
                    Object result = supplier.get();
                    return new ToolResult(result.toString());
                }
        );
    }

    // TODO test
    public static <T> ToolThingy<T> from(String name,
                                         String description,
                                         Class<T> argumentClass,
                                         Consumer<T> consumer) {
        return ToolThingy.<T>builder()
                .name(name)
                .description(description)
                .argumentClass(argumentClass)
                .callback(request -> {
                    consumer.accept(request.argument());
                    return new ToolResult(null); // TODO "Success"?
                })
                .build();
    }
}