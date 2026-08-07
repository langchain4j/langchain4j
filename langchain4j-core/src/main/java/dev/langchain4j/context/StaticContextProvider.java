package dev.langchain4j.context;

import dev.langchain4j.Experimental;
import dev.langchain4j.rag.content.Content;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * A {@link ContextProvider} that always returns the same pre-loaded content.
 * <p>
 * This is the core "C" in CAG: domain knowledge, policies, instructions, or
 * reference material that should always be available to the LLM.
 * <p>
 * Example:
 * <pre>
 * ContextProvider policies = StaticContextProvider.of(
 *     "All responses must comply with GDPR.",
 *     "Customer data must not be shared externally."
 * );
 * </pre>
 *
 * @see ContextProvider
 */
@Experimental
public class StaticContextProvider implements ContextProvider {

    private final List<Content> contents;
    private final String name;

    private StaticContextProvider(String name, List<Content> contents) {
        this.name = getOrDefault(name, "StaticContextProvider");
        this.contents = copy(ensureNotEmpty(contents, "contents"));
    }

    @Override
    public List<Content> provideContext(ContextRequest request) {
        return contents;
    }

    @Override
    public String name() {
        return name;
    }

    /**
     * Creates a provider from one or more text strings.
     */
    public static StaticContextProvider of(String... texts) {
        ensureNotEmpty(Arrays.asList(texts), "texts");
        List<Content> contents = Arrays.stream(texts)
                .map(Content::from)
                .collect(Collectors.toList());
        return new StaticContextProvider(null, contents);
    }

    /**
     * Creates a provider from one or more {@link Content} objects.
     */
    public static StaticContextProvider of(Content... contents) {
        return new StaticContextProvider(null, Arrays.asList(contents));
    }

    /**
     * Creates a provider from a list of {@link Content} objects.
     */
    public static StaticContextProvider of(List<Content> contents) {
        return new StaticContextProvider(null, contents);
    }

    public static StaticContextProviderBuilder builder() {
        return new StaticContextProviderBuilder();
    }

    public static class StaticContextProviderBuilder {

        private String name;
        private final List<Content> contents = new ArrayList<>();

        StaticContextProviderBuilder() {
        }

        public StaticContextProviderBuilder name(String name) {
            this.name = name;
            return this;
        }

        public StaticContextProviderBuilder content(Content content) {
            this.contents.add(ensureNotNull(content, "content"));
            return this;
        }

        public StaticContextProviderBuilder content(String text) {
            this.contents.add(Content.from(ensureNotBlank(text, "text")));
            return this;
        }

        public StaticContextProviderBuilder contents(List<Content> contents) {
            this.contents.addAll(ensureNotEmpty(contents, "contents"));
            return this;
        }

        public StaticContextProvider build() {
            return new StaticContextProvider(name, contents);
        }
    }
}
