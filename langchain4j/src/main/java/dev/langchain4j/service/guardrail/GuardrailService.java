package dev.langchain4j.service.guardrail;

import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.internal.AsyncNotSupported;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.guardrail.spi.GuardrailServiceBuilderFactory;
import java.lang.reflect.Method;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;

/**
 * Defines a service for executing guardrails associated with methods in an AI service.
 * Guardrails are constraints or validations applied either to input or output of a method.
 * <p>
 *     <strong>Method keys.</strong> The guardrails of a method are looked up by a key. An AI service identifies the
 *     method it invokes with its {@link Method}; the default service translates that into the key its guardrails were
 *     registered under with
 *     {@link dev.langchain4j.spi.classloading.ClassMetadataProviderFactory#resolveMethodKey(Object)}. That is the same
 *     for every lookup of this interface, blocking or not.
 * </p>
 * <p>
 *     <strong>Customizing and extending.</strong> What is shared by every kind of guardrail (looking up the guardrails
 *     to run for a key, and running them) lives in {@link GenericGuardrailService}, which this interface specializes for
 *     AI services. Frameworks customize AI service guardrails through the SPIs the default builder and service use:
 *     {@link dev.langchain4j.spi.classloading.ClassMetadataProviderFactory} (how methods and annotations are found,
 *     and how the method passed at invocation time maps to a method key) and
 *     {@link dev.langchain4j.spi.classloading.ClassInstanceFactory} (how guardrail classes become instances), or by
 *     replacing the whole builder with a {@link dev.langchain4j.service.guardrail.spi.GuardrailServiceBuilderFactory}.
 *     {@link AbstractGuardrailService} is deprecated: it was the way to provide a custom service before these SPIs were
 *     enough, and is only kept for the frameworks that subclass it.
 * </p>
 */
public non-sealed interface GuardrailService
        extends GenericGuardrailService<
                InputGuardrailRequest, InputGuardrailResult, OutputGuardrailRequest, OutputGuardrailResult> {

    // executeInputGuardrails, executeOutputGuardrails, hasInputGuardrails, hasOutputGuardrails and the asynchronous
    // variants are declared again, with the AI service types, for binary compatibility with code compiled before this
    // interface extended GenericGuardrailService. A method's JVM signature includes its parameter and return types, and
    // the inherited methods have the request and result types erased to GuardrailRequest and GuardrailResult. Without
    // these declarations, a caller would bind to the inherited signature and bypass an
    // implementation compiled against the old interface, such as a framework subclass that overrides these methods to
    // translate its method keys, and the guardrails of its methods would silently not run. They can be removed when the
    // next major release no longer needs to run code compiled against the old interface.

    /**
     * Retrieves the class representing the AI service to which the guardrails apply.
     *
     * @return The {@code Class} object representing the AI service.
     * @deprecated use {@link #targetClass()} instead
     */
    @Deprecated(since = "1.25.0")
    Class<?> aiServiceClass();

    /**
     * The class representing the AI service to which the guardrails apply. By default, it is the
     * {@link #aiServiceClass()}, so that implementations written before {@code targetClass()} existed keep working.
     *
     * @return The {@code Class} object representing the AI service.
     * @since 1.25.0
     */
    @Override
    default Class<?> targetClass() {
        return aiServiceClass();
    }

    /** {@inheritDoc} */
    @Override
    <MethodKey> InputGuardrailResult executeInputGuardrails(MethodKey method, InputGuardrailRequest request);

    /**
     * Non-blocking counterpart of {@link #executeInputGuardrails(Object, InputGuardrailRequest)}.
     * <p>
     * The default implementation returns a failed future carrying {@link AsyncNotSupportedException}; the internal implementation
     * ({@code AbstractKeyedGuardrailService}) overrides it, and only implementors that opt into the non-blocking
     * AI Service modes need to provide it.
     *
     * @param method The method whose input guardrails are to be executed.
     * @param request The parameters to validate against the input guardrails. Must not be null.
     * @return A {@link CompletableFuture} that completes with the {@link InputGuardrailResult}.
     * @param <MethodKey> The type of the method key, representing a unique identifier for methods.
     * @since 1.20.0
     */
    @Override
    @Experimental
    default <MethodKey> CompletableFuture<InputGuardrailResult> executeInputGuardrailsAsync(
            MethodKey method, InputGuardrailRequest request) {
        return AsyncNotSupported.failedFuture(getClass(), "executeInputGuardrailsAsync");
    }

    /**
     * Executes the input guardrails associated with the given method and parameters,
     * and retrieves a modified or validated {@link UserMessage} based on the result.
     *
     * @param <MethodKey> The type of the method key, representing a unique identifier for methods.
     * @param method The method whose input guardrails are to be executed. Nullable.
     * @param request The parameters to validate against the input guardrails. Must not be null.
     * @return A {@link UserMessage} derived from the provided parameters and the result
     *         of the input guardrails execution. If guardrails are applied successfully,
     *         a potentially rewritten user message is returned. If no guardrails are
     *         associated with the method, the original user message is returned.
     */
    default <MethodKey> UserMessage executeGuardrails(MethodKey method, InputGuardrailRequest request) {
        return executeInputGuardrails(method, request).userMessage(request);
    }

    /**
     * Non-blocking counterpart of {@link #executeGuardrails(Object, InputGuardrailRequest)}: runs the input
     * guardrails without blocking the calling thread and yields the (possibly rewritten) {@link UserMessage}.
     *
     * @param <MethodKey> The type of the method key, representing a unique identifier for methods.
     * @param method The method whose input guardrails are to be executed. Nullable.
     * @param request The parameters to validate against the input guardrails. Must not be null.
     * @return A {@link CompletableFuture} that completes with the (possibly rewritten) {@link UserMessage}.
     * @since 1.20.0
     */
    @Experimental
    default <MethodKey> CompletableFuture<UserMessage> executeGuardrailsAsync(
            MethodKey method, InputGuardrailRequest request) {
        CompletableFuture<InputGuardrailResult> guardrailFuture = executeInputGuardrailsAsync(method, request);
        CompletableFuture<UserMessage> result = guardrailFuture.thenApply(r -> r.userMessage(request));
        propagateCancellation(result, guardrailFuture);
        return result;
    }

    /** {@inheritDoc} */
    @Override
    <MethodKey> OutputGuardrailResult executeOutputGuardrails(MethodKey method, OutputGuardrailRequest request);

    /**
     * Non-blocking counterpart of {@link #executeOutputGuardrails(Object, OutputGuardrailRequest)}.
     * <p>
     * The default implementation returns a failed future carrying {@link AsyncNotSupportedException}; the internal implementation
     * ({@code AbstractKeyedGuardrailService}) overrides it, and only implementors that opt into the non-blocking
     * AI Service modes need to provide it.
     *
     * @param method The method whose output guardrails are to be executed.
     * @param request The parameters to validate against the output guardrails. Must not be null.
     * @return A {@link CompletableFuture} that completes with the {@link OutputGuardrailResult}.
     * @param <MethodKey> The type of the method key, representing a unique identifier for methods.
     * @since 1.20.0
     */
    @Override
    @Experimental
    default <MethodKey> CompletableFuture<OutputGuardrailResult> executeOutputGuardrailsAsync(
            MethodKey method, OutputGuardrailRequest request) {
        return AsyncNotSupported.failedFuture(getClass(), "executeOutputGuardrailsAsync");
    }

    /** {@inheritDoc} */
    @Override
    <MethodKey> boolean hasInputGuardrails(MethodKey method);

    /** {@inheritDoc} */
    @Override
    <MethodKey> boolean hasOutputGuardrails(MethodKey method);

    /**
     * Executes the guardrails associated with a given method and parameters, returning the appropriate response.
     *
     * @param <MethodKey> The type of the method key, representing a unique identifier for methods.
     * @param <T> The type of response to produce
     * @param method The method whose output guardrails are to be executed. Nullable.
     * @param request The parameters to validate against the output guardrails. Must not be null.
     * @return A {@link ChatResponse} that encapsulates the output of executing the guardrails based on the provided parameters.
     */
    default <MethodKey, T> T executeGuardrails(MethodKey method, OutputGuardrailRequest request) {
        return executeOutputGuardrails(method, request).response(request);
    }

    /**
     * Non-blocking counterpart of {@link #executeGuardrails(Object, OutputGuardrailRequest)} for the asynchronous
     * ({@code CompletableFuture}) and reactive ({@code Flow.Publisher}) AI Service modes. The output guardrails —
     * including any reprompt round-trips to the model — run without blocking the calling thread; a guardrail that
     * performs blocking I/O keeps the calling thread free only if it overrides
     * {@link dev.langchain4j.guardrail.Guardrail#validateAsync(dev.langchain4j.guardrail.GuardrailRequest)}.
     *
     * @param <MethodKey> The type of the method key, representing a unique identifier for methods.
     * @param <T> The type of response to produce
     * @param method The method whose output guardrails are to be executed. Nullable.
     * @param request The parameters to validate against the output guardrails. Must not be null.
     * @return A {@link CompletableFuture} that completes with the response to use once the output guardrails have run.
     * @since 1.20.0
     */
    @Experimental
    default <MethodKey, T> CompletableFuture<T> executeGuardrailsAsync(
            MethodKey method, OutputGuardrailRequest request) {
        CompletableFuture<OutputGuardrailResult> guardrailFuture = executeOutputGuardrailsAsync(method, request);
        CompletableFuture<T> result = guardrailFuture.thenApply(r -> r.response(request));
        propagateCancellation(result, guardrailFuture);
        return result;
    }

    /**
     * Creates a new instance of {@link Builder} for the specified AI service class.
     * <p>
     *     Attempts to retrieve an instance through a {@link dev.langchain4j.service.guardrail.spi.GuardrailServiceBuilderFactory}, if available.
     *     If no factory is present, it uses its own default instance.
     * </p>
     *
     * @param aiServiceClass The {@code Class} object representing the AI service for which the builder is being created.
     * @return A {@link Builder} instance initialized with the specified AI service class.
     */
    static Builder builder(Class<?> aiServiceClass) {
        return ServiceLoader.load(GuardrailServiceBuilderFactory.class)
                .findFirst()
                .map(builderFactory -> builderFactory.getBuilder(aiServiceClass))
                .orElseGet(() -> new GuardrailServiceBuilder(aiServiceClass));
    }

    /**
     * Builder class for building {@link GuardrailService} instances.
     * <p>
     *     It is the builder of the AI service kind of the {@link GenericGuardrailService.Builder} hierarchy.
     * </p>
     */
    non-sealed interface Builder
            extends GenericGuardrailService.Builder<
                    InputGuardrailRequest,
                    InputGuardrailResult,
                    OutputGuardrailRequest,
                    OutputGuardrailResult,
                    GuardrailService,
                    InputGuardrail,
                    OutputGuardrail,
                    dev.langchain4j.guardrail.config.InputGuardrailsConfig,
                    dev.langchain4j.guardrail.config.OutputGuardrailsConfig,
                    Builder> {

        // Every method of GenericGuardrailService.Builder is declared again with the AI service types, for binary
        // compatibility with code compiled before this interface extended GenericGuardrailService.Builder. A method's
        // JVM signature includes its return type, and the inherited methods return the builder type as a type
        // variable, which the JVM sees as GenericGuardrailService.Builder (and build() as GenericGuardrailService).
        // - The abstract methods keep the signatures that the builders of frameworks compiled against the old interface
        //   provide, and that callers compiled against it use. Without them, such a caller fails with a
        //   NoSuchMethodError, and a caller compiled against this version fails with an AbstractMethodError on such a
        //   builder.
        // - The default (varargs) methods keep the signatures that callers compiled against the old interface use.
        //   They only delegate to the inherited implementation.
        // They can be removed when the next major release no longer needs to run code compiled against the old
        // interface.

        /** {@inheritDoc} */
        @Override
        Builder inputGuardrailsConfig(dev.langchain4j.guardrail.config.InputGuardrailsConfig config);

        /** {@inheritDoc} */
        @Override
        Builder outputGuardrailsConfig(dev.langchain4j.guardrail.config.OutputGuardrailsConfig config);

        /** {@inheritDoc} */
        @Override
        <I extends InputGuardrail> Builder inputGuardrailClasses(List<Class<? extends I>> guardrailClasses);

        /** {@inheritDoc} */
        @Override
        @SuppressWarnings("unchecked")
        default <I extends InputGuardrail> Builder inputGuardrailClasses(Class<? extends I>... guardrailClasses) {
            return GenericGuardrailService.Builder.super.inputGuardrailClasses(guardrailClasses);
        }

        /** {@inheritDoc} */
        @Override
        <O extends OutputGuardrail> Builder outputGuardrailClasses(List<Class<? extends O>> guardrailClasses);

        /** {@inheritDoc} */
        @Override
        @SuppressWarnings("unchecked")
        default <O extends OutputGuardrail> Builder outputGuardrailClasses(Class<? extends O>... guardrailClasses) {
            return GenericGuardrailService.Builder.super.outputGuardrailClasses(guardrailClasses);
        }

        /** {@inheritDoc} */
        @Override
        <I extends InputGuardrail> Builder inputGuardrails(List<I> guardrails);

        /** {@inheritDoc} */
        @Override
        @SuppressWarnings("unchecked")
        default <I extends InputGuardrail> Builder inputGuardrails(I... guardrails) {
            return GenericGuardrailService.Builder.super.inputGuardrails(guardrails);
        }

        /** {@inheritDoc} */
        @Override
        <O extends OutputGuardrail> Builder outputGuardrails(List<O> guardrails);

        /** {@inheritDoc} */
        @Override
        @SuppressWarnings("unchecked")
        default <O extends OutputGuardrail> Builder outputGuardrails(O... guardrails) {
            return GenericGuardrailService.Builder.super.outputGuardrails(guardrails);
        }

        /**
         * Builds and returns an instance of {@link GuardrailService}.
         * This method configures input and output guardrails for each method of the service.
         * Guardrails set in the builder take precedence. If none are set, it uses the
         * method-level annotations, and if those are absent, the class-level annotations.
         * <p>
         * The guardrails and their configuration are resolved independently of each other, and separately for input
         * and output guardrails. Only the first source found is used: guardrails set in the builder, on the method and
         * on the class are not combined. Guardrails set in the builder count only when there is at least one:
         * {@code null} or an empty list does not disable the guardrails declared in annotations. Guardrail instances
         * and guardrail classes set in the builder are both used: the instances first, then the classes.
         *
         * @return an instance of {@link GuardrailService} configured with appropriate
         * input and output guardrails.
         */
        @Override
        GuardrailService build();
    }
}
