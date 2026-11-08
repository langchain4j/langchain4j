package dev.langchain4j.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import dev.langchain4j.internal.Json;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The document being read must not end up in a failure message, because that message is logged and
 * the document is untrusted input. Nothing in LangChain4j puts it there - but Jackson would, if
 * {@code INCLUDE_SOURCE_IN_LOCATION} were on, by appending the whole document as the location of
 * the failure. It is off by default in both Jackson versions, so the promise rests on that default.
 * This is what notices if the default moves.
 *
 * <p>Written against the message rather than the exception type, because the type differs by
 * codec: the Jackson 2 codecs still wrap the library's own exception, while
 * {@code langchain4j-jackson3} reports {@link JsonReadException}. The guarantee is the same
 * either way, and its Jackson 3 half is pinned by {@code Jackson3ParityTest}.
 */
class JsonExceptionMessageTest {

    static class Pojo {
        public String name;
        public int age;
    }

    private static final String SECRET = "sk-not-a-real-key-2f7a";

    /** Malformed after the secret, so the secret can only reach a message as the source document. */
    private static final String MALFORMED = "{\"name\":\"" + SECRET + "\", \"age\": }";

    private static List<String> messagesOf(Throwable thrown) {
        List<String> messages = new ArrayList<>();
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            if (t.getMessage() != null) {
                messages.add(t.getMessage());
            }
        }
        return messages;
    }

    @Test
    void the_document_being_read_is_not_in_any_message() {
        Throwable thrown = catchThrowable(() -> Json.fromJson(MALFORMED, Pojo.class));

        assertThat(thrown).isNotNull();
        assertThat(messagesOf(thrown))
                .isNotEmpty()
                .allSatisfy(message -> assertThat(message).doesNotContain(SECRET))
                .anySatisfy(message -> assertThat(message).contains("Unexpected character"));
    }

    /**
     * The guarantee is that the document is not carried, not that the message is sanitised: the
     * reason a value was rejected names that value. Stated here so it is not mistaken for the one
     * above.
     */
    @Test
    void a_value_that_could_not_be_converted_does_appear() {
        Throwable thrown = catchThrowable(() -> Json.fromJson("{\"age\":\"" + SECRET + "\"}", Pojo.class));

        assertThat(thrown).isNotNull();
        assertThat(messagesOf(thrown)).anySatisfy(message -> assertThat(message).contains(SECRET));
    }
}
