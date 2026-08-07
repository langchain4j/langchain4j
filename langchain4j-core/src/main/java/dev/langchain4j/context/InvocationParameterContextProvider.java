package dev.langchain4j.context;

import dev.langchain4j.Experimental;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.rag.content.Content;

import java.util.List;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

/**
 * A {@link ContextProvider} that extracts context from {@link InvocationParameters} using a configured key.
 * <p>
 * This enables per-request context injection from calling code:
 * <pre>
 * // Provider configured once:
 * ContextProvider userContext = InvocationParameterContextProvider.of("userProfile");
 *
 * // At invocation time:
 * InvocationParameters params = InvocationParameters.from("userProfile",
 *     "Role: admin. Department: Engineering.");
 * assistant.chat("Show me project data", params);
 * </pre>
 * <p>
 * The value stored under the key is converted to {@link Content} as follows:
 * <ul>
 *   <li>{@code null} &rarr; empty list (graceful degradation)</li>
 *   <li>{@link Content} &rarr; singleton list</li>
 *   <li>{@code List<Content>} &rarr; returned as-is</li>
 *   <li>{@link String} &rarr; wrapped in {@code Content.from(string)}</li>
 *   <li>Any other type &rarr; wrapped in {@code Content.from(value.toString())}</li>
 * </ul>
 *
 * @see ContextProvider
 */
@Experimental
public class InvocationParameterContextProvider implements ContextProvider {

    private final String parameterKey;

    private InvocationParameterContextProvider(String parameterKey) {
        this.parameterKey = ensureNotBlank(parameterKey, "parameterKey");
    }

    /**
     * Creates a provider that reads context from the given {@link InvocationParameters} key.
     *
     * @param parameterKey the key to look up in invocation parameters
     * @return a new provider
     */
    public static InvocationParameterContextProvider of(String parameterKey) {
        return new InvocationParameterContextProvider(parameterKey);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Content> provideContext(ContextRequest request) {
        InvocationParameters params = request.invocationParameters();
        if (params == null) {
            return emptyList();
        }
        Object value = params.get(parameterKey);
        if (value == null) {
            return emptyList();
        }
        if (value instanceof Content) {
            return singletonList((Content) value);
        }
        if (value instanceof List<?>) {
            return ((List<?>) value).stream()
                    .filter(Content.class::isInstance)
                    .map(Content.class::cast)
                    .collect(Collectors.toList());
        }
        if (value instanceof String) {
            return singletonList(Content.from((String) value));
        }
        return singletonList(Content.from(value.toString()));
    }

    @Override
    public String name() {
        return "InvocationParameterContextProvider[" + parameterKey + "]";
    }
}
