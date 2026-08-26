package dev.langchain4j.json.jackson3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.scope.AgenticScopeSerializer;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.scope.UnserializableAgenticScopeException;
import dev.langchain4j.data.message.UserMessage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * AgenticScope state is written with type information, so reading it back means instantiating
 * classes named in the document. These check that the Jackson 3 codec is the one in use, that it
 * round-trips state, and - the part that matters - that it applies the same allowlist as the
 * Jackson 2 codec rather than accepting anything.
 */
class Jackson3AgenticScopeJsonCodecTest {

    /** A type an application would have to register before its state can be read back. */
    public record Order(String id, int quantity) {}

    private static DefaultAgenticScope scopeWith(String key, Object value) {
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        scope.writeState(key, value);
        return scope;
    }

    @Test
    void the_jackson3_codec_is_the_one_in_use() {
        String json = AgenticScopeSerializer.toJson(scopeWith("greeting", "hello"));

        // Round-tripping through the serializer exercises whichever codec the ServiceLoader found.
        assertThat(AgenticScopeSerializer.fromJson(json).readState("greeting")).isEqualTo("hello");
    }

    @Test
    void round_trips_state_of_allowlisted_types() {
        DefaultAgenticScope scope = scopeWith("numbers", List.of(1, 2, 3));
        scope.writeState("message", UserMessage.from("hi"));
        scope.writeState("lookup", Map.of("k", "v"));

        DefaultAgenticScope read = AgenticScopeSerializer.fromJson(AgenticScopeSerializer.toJson(scope));

        assertThat(read.readState("numbers")).isEqualTo(List.of(1, 2, 3));
        assertThat(read.readState("message")).isEqualTo(UserMessage.from("hi"));
        assertThat(read.readState("lookup")).isEqualTo(Map.of("k", "v"));
    }

    @Test
    void refuses_a_type_that_was_never_allowed() {
        String json = AgenticScopeSerializer.toJson(scopeWith("order", new Order("A-1", 2)));

        assertThatThrownBy(() -> AgenticScopeSerializer.fromJson(json))
                .isInstanceOf(UnserializableAgenticScopeException.class);
    }

    @Test
    void accepts_a_type_once_the_application_registers_it() {
        String json = AgenticScopeSerializer.toJson(scopeWith("order", new Order("A-2", 5)));

        AgenticScopeSerializer.allowDeserializationType(Order.class);

        assertThat(AgenticScopeSerializer.fromJson(json).readState("order")).isEqualTo(new Order("A-2", 5));
    }
}
