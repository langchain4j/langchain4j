package dev.langchain4j.model.decision.request;

import dev.langchain4j.Experimental;

/**
 * The kind of a {@link DecisionInput}. More kinds can be added in future releases, so a {@code switch} over it
 * should have a {@code default} branch, for example one that throws an
 * {@link dev.langchain4j.exception.UnsupportedFeatureException}.
 *
 * @since 1.23.0
 */
@Experimental
public enum DecisionInputType {

    /**
     * Plain text, see {@link TextInput}.
     */
    TEXT,

    /**
     * Named fields, see {@link FieldsInput}.
     */
    FIELDS,

    /**
     * A list of contents, such as text and images, see {@link ContentsInput}.
     */
    CONTENTS,

    /**
     * A conversation, see {@link MessagesInput}.
     */
    MESSAGES
}
