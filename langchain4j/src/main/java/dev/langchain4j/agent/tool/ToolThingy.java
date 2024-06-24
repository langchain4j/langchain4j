package dev.langchain4j.agent.tool;

import dev.langchain4j.Experimental;
import lombok.AllArgsConstructor;

import java.lang.reflect.Field;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static dev.langchain4j.agent.tool.ToolSpecifications.toJsonSchemaProperties;

/**
 * TODO
 */
@Experimental
@AllArgsConstructor // TODO
public class ToolThingy<T> {
    // TODO name
    // TODO location

    private final String name;
    private final String description;
    // TODO names/types/descriptions of arguments should probably also be dynamic?
    private final Class<T> argumentClass; // TODO needed? can get from function?
    private final ToolCallback<T> callback;

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public Class<T> argumentClass() {
        return argumentClass;
    }

    public ToolCallback<T> callback() {
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
        }

        return builder.build();
    }

    // TODO ctor? builder?
    public static <T> ToolThingy<T> from(String name,
                                         String description,
                                         Class<T> argumentClass,
                                         ToolCallback<T> callback) {
        return new ToolThingy<>(name, description, argumentClass, callback);
    }

    // TODO test
    public static <T> ToolThingy<T> from(String name,
                                         String description,
                                         Class<T> argumentClass,
                                         Function<T, ?> function) {
        return new ToolThingy<>(
                name,
                description,
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
                request -> {
                    Object result = supplier.get();
                    return new ToolResult(result.toString());
                }
        );
    }

    // TODO test
    public static <T> ToolThingy<T> from(String name,
                                         String description,
                                         Consumer<T> supplier) {
        return new ToolThingy<>(
                name,
                description,
                null,
                request -> {
                    supplier.accept(request.argument());
                    return new ToolResult(null);
                }
        );
    }
}