package dev.langchain4j.model.decision.request;

import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.Content;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A {@link DecisionInput} that is a list of contents, for example a {@link dev.langchain4j.data.message.TextContent}
 * and an {@link dev.langchain4j.data.message.ImageContent}. Which kinds of contents are supported depends on the
 * decision model.
 *
 * @since 1.23.0
 */
@Experimental
public final class ContentsInput implements DecisionInput {

    private final List<Content> contents;

    private ContentsInput(List<? extends Content> contents) {
        List<Content> copy = new ArrayList<>(ensureNotEmpty(contents, "contents"));
        if (copy.contains(null)) {
            throw new IllegalArgumentException("contents cannot contain null");
        }
        this.contents = Collections.unmodifiableList(copy);
    }

    /**
     * Creates the input from a copy of the given contents.
     *
     * @throws IllegalArgumentException if {@code contents} is {@code null} or empty, or contains {@code null}
     */
    public static ContentsInput from(List<? extends Content> contents) {
        return new ContentsInput(contents);
    }

    /**
     * The contents, in order. The list is unmodifiable.
     */
    public List<Content> contents() {
        return contents;
    }

    @Override
    public DecisionInputType type() {
        return DecisionInputType.CONTENTS;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ContentsInput that && contents.equals(that.contents);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contents);
    }

    @Override
    public String toString() {
        return "ContentsInput{types=" + contents.stream().map(Content::type).toList() + '}';
    }
}
