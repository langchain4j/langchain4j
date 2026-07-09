package dev.langchain4j.model.embedding.request;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toCollection;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.segment.TextSegment;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A single input to embed: one or more ordered {@link Content} parts that are fused into <b>one</b>
 * {@link dev.langchain4j.data.embedding.Embedding}.
 * <p>
 * A plain text input is simply a single {@link TextContent}. Multimodal models can accept an interleaving of
 * text and image/video/audio parts (e.g. Cohere Embed v4, Voyage multimodal) — the whole interleaved list
 * produces a single vector.
 * <p>
 * An {@link EmbeddingRequest} carries a <i>list</i> of {@link EmbeddingInput}s (the batch dimension), each of
 * which carries a list of {@link Content} parts (the interleaving dimension).
 *
 * @since 1.18.0
 */
@Experimental
public class EmbeddingInput {

    private final List<Content> contents;

    protected EmbeddingInput(List<Content> contents) {
        this.contents = copy(ensureNotEmpty(contents, "contents"));
    }

    /**
     * The ordered content parts that together produce a single embedding.
     */
    public List<Content> contents() {
        return contents;
    }

    /**
     * The set of {@link ContentType}s present in this input. An {@link
     * dev.langchain4j.model.embedding.EmbeddingModel} validates these against its
     * {@link dev.langchain4j.model.embedding.EmbeddingModel#supportedContentTypes() supported content types}.
     */
    public Set<ContentType> contentTypes() {
        return contents.stream().map(Content::type).collect(toCollection(LinkedHashSet::new));
    }

    /**
     * The concatenated text of all {@link TextContent} parts of this input, used by text-only models. For a
     * plain single-text input this is just its text.
     */
    public String text() {
        return contents.stream()
                .filter(content -> content instanceof TextContent)
                .map(content -> ((TextContent) content).text())
                .collect(joining()); // TODO separate with newline?
    }

    public static EmbeddingInput from(String text) {
        return new EmbeddingInput(List.of(TextContent.from(text)));
    }

    public static EmbeddingInput from(TextSegment segment) {
        return from(segment.text());
    }

    public static EmbeddingInput from(Content... contents) {
        return new EmbeddingInput(Arrays.asList(contents));
    }

    public static EmbeddingInput from(List<Content> contents) {
        return new EmbeddingInput(contents);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmbeddingInput that = (EmbeddingInput) o;
        return Objects.equals(contents, that.contents);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contents);
    }

    @Override
    public String toString() {
        return "EmbeddingInput{contents=" + contents + '}';
    }
}
