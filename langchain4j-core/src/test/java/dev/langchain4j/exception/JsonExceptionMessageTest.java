package dev.langchain4j.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.internal.Json;
import org.junit.jupiter.api.Test;

/**
 * {@link JsonException} promises that the document being read does not end up in the message,
 * because that message is logged and the document is untrusted input. Nothing in LangChain4j puts
 * it there - but Jackson would, if {@code INCLUDE_SOURCE_IN_LOCATION} were on, by appending the
 * whole document as the location of the failure. It is off by default in both Jackson versions,
 * so the promise currently rests on that default. This is what notices if the default moves.
 */
class JsonExceptionMessageTest {

    static class Pojo {
        public String name;
        public int age;
    }

    private static final String SECRET = "sk-not-a-real-key-2f7a";

    /** Malformed after the secret, so the secret can only reach the message as the source. */
    private static final String MALFORMED = "{\"name\":\"" + SECRET + "\", \"age\": }";

    @Test
    void the_document_being_read_is_not_in_the_message() {
        assertThatThrownBy(() -> Json.fromJson(MALFORMED, Pojo.class))
                .isInstanceOf(JsonReadException.class)
                .hasMessageContaining("Unexpected character")
                .hasMessageNotContaining(SECRET);
    }

    @Test
    void the_document_being_read_is_not_in_any_message_up_the_cause_chain() {
        Throwable thrown = null;
        try {
            Json.fromJson(MALFORMED, Pojo.class);
        } catch (JsonReadException e) {
            thrown = e;
        }

        assertThat(thrown).isNotNull();
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            assertThat(t.getMessage()).doesNotContain(SECRET);
        }
    }

    /**
     * The guarantee is that the document is not carried, not that the message is sanitised: the
     * reason a value was rejected names that value. Stated here so it is not mistaken for the one
     * above.
     */
    @Test
    void a_value_that_could_not_be_converted_does_appear() {
        assertThatThrownBy(() -> Json.fromJson("{\"age\":\"" + SECRET + "\"}", Pojo.class))
                .isInstanceOf(JsonReadException.class)
                .hasMessageContaining(SECRET);
    }
}
