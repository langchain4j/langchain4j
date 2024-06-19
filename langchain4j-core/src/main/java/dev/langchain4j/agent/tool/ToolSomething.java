package dev.langchain4j.agent.tool;

import lombok.AllArgsConstructor;

import java.util.function.Function;

/**
 * TODO
 */
@AllArgsConstructor // TODO
public class ToolSomething {
    // TODO name
    // TODO location

    private final String name;
    private final String description;
    private final Class<?> argumentClass; // TODO needed? can get from function?
    private final Function<?, ?> function;

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public Class<?> argumentClass() {
        return argumentClass;
    }

    public Function<?, ?> function() {
        return function;
    }

    // TODO ctor? builder?
    public static <T> ToolSomething from(String name,
                                         String description,
                                         Class<T> argumentClass,
                                         Function<T, ?> function) {
        return new ToolSomething(name, description, argumentClass, function);
    }
}