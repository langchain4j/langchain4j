package dev.langchain4j.rag.query.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.test.mock.MockChatModel;

/**
 * Helpers for the query transformer tests of this package.
 */
final class TransformerTestModels {

    private TransformerTestModels() {}

    /**
     * The text of the only message of the only request the mock received.
     */
    static String userMessageText(MockChatModel model) {
        assertThat(model.request().messages()).hasSize(1);
        return ((UserMessage) model.request().messages().get(0)).singleText();
    }
}
