package dev.langchain4j.service.tool;

import java.lang.reflect.Type;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.internal.Json;

/**
 * FunctionToolExecutor is a flexible and extensible tool executor designed for dynamically generating ToolExecutor
 * instances within the langchain4j framework. Compared to the traditional @Tool annotation-based approach,
 * FunctionToolExecutor provides greater flexibility, making it suitable for complex business scenarios
 * and dynamic tool execution needs.
 * <p>
 * Dynamic Generation: Allows for dynamically generating and configuring ToolExecutor instances based on specific
 * application requirements, enabling runtime flexibility.
 * <p>
 * Flexible Configuration: Provides more flexibility than the @Tool annotation approach, supporting dynamic parameter
 * passing and handling different input-output types.
 * <p>
 * Enhanced Extensibility: Offers a highly extensible framework, allowing developers to insert custom logic and plugins
 * during execution to meet diverse business needs.
 * <p>
 * Simplified Tool Management: By using FunctionToolExecutor, various tools can be managed and executed without relying
 * on static configurations and annotations, increasing development efficiency and flexibility.
 */
public class FunctionToolExecutor<I, O> implements ToolExecutor {

    public static FunctionToolExecutor<String, String> from(Function<String, String> function) {
        return new FunctionToolExecutor<>(toolFunction(function), Function.identity(), defaultResultConverter());
    }

    public static <I, O> FunctionToolExecutor<I, O> from(Function<I, O> function, Type inputType) {
        return new FunctionToolExecutor<>(toolFunction(function), inputTypeParser(inputType), defaultResultConverter());
    }

    public static <I, O> FunctionToolExecutor<I, O> from(Function<I, O> function, Class<I> inputClass) {
        return new FunctionToolExecutor<>(toolFunction(function), inputTypeParser(inputClass), defaultResultConverter());
    }

    public static <I> FunctionToolExecutor<I, String> from(Consumer<I> consumer, Type inputType) {
        return new FunctionToolExecutor<>(toolFunction(consumer), inputTypeParser(inputType), defaultResultConverter());
    }

    public static <I> FunctionToolExecutor<I, String> from(Consumer<I> consumer, Class<I> inputClass) {
        return new FunctionToolExecutor<>(toolFunction(consumer), inputTypeParser(inputClass), defaultResultConverter());
    }

    public static <I> FunctionToolExecutor<I, String> from(Function<I, String> function, Function<String, I> inputTypeParser) {
        return new FunctionToolExecutor<>(toolFunction(function), inputTypeParser, Function.identity());
    }

    public static <I, O> FunctionToolExecutor<I, O> from(BiFunction<I, Context, O> toolFunction,
                                                         Function<String, I> inputTypeParser,
                                                         Function<O, String> toolCallResultConverter
    ) {
        return new FunctionToolExecutor<>(toolFunction, inputTypeParser, toolCallResultConverter);
    }

    // =========== toolFunction ========

    public static <I, O> BiFunction<I, Context, O> toolFunction(Function<I, O> function) {
        return (in, context) -> function.apply(in);
    }

    public static <I> BiFunction<I, Context, String> toolFunction(Consumer<I> consumer) {
        return (in, context) -> {
            consumer.accept(in);
            return null;
        };
    }

    // ========== inputTypeParser =============

    public static <I> Function<String, I> inputTypeParser(Type type) {
        return input -> Json.fromJson(input, type);
    }

    public static <I> Function<String, I> inputTypeParser(Class<I> clazz) {
        return input -> Json.fromJson(input, clazz);
    }

    // =========== toolCallResultConverter =============

    public static <O> Function<O, String> defaultResultConverter() {
        return result -> {
            if (result == null) {
                return "Success";
            } else if (result instanceof String) {
                return (String) result;
            } else {
                return Json.toJson(result);
            }
        };
    }

    private final BiFunction<I, Context, O> toolFunction;
    private final Function<String, I> inputTypeParser;
    private final Function<O, String> toolCallResultConverter;

    public FunctionToolExecutor(BiFunction<I, Context, O> toolFunction,
                                Function<String, I> inputTypeParser,
                                Function<O, String> toolCallResultConverter
    ) {
        this.toolFunction = toolFunction;
        this.inputTypeParser = inputTypeParser;
        this.toolCallResultConverter = toolCallResultConverter;
    }

    @Override
    public String execute(ToolExecutionRequest toolExecutionRequest, Object memoryId) {
        I arguments = inputTypeParser.apply(toolExecutionRequest.arguments());

        Context context = new Context(toolExecutionRequest, memoryId);

        O result = toolFunction.apply(arguments, context);

        return toolCallResultConverter.apply(result);
    }

    public record Context(ToolExecutionRequest toolExecutionRequest, Object memoryId) {
    }

}

