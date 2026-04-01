package dev.langchain4j.model.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChatRequestOptionsTest {

    @Test
    void empty_should_have_no_attributes() {
        assertThat(ChatRequestOptions.EMPTY.listenerAttributes()).isEmpty();
    }

    @Test
    void should_build_with_single_attribute() {

        // when
        ChatRequestOptions options = ChatRequestOptions.builder()
                .addListenerAttribute("key", "value")
                .build();

        // then
        assertThat(options.listenerAttributes()).containsEntry("key", "value");
    }

    @Test
    void should_build_with_bulk_attributes() {

        // when
        ChatRequestOptions options = ChatRequestOptions.builder()
                .listenerAttributes(Map.of("a", 1, "b", 2))
                .build();

        // then
        assertThat(options.listenerAttributes()).containsEntry("a", 1).containsEntry("b", 2);
    }

    @Test
    void bulk_should_replace_previous_attributes() {

        // when
        ChatRequestOptions options = ChatRequestOptions.builder()
                .addListenerAttribute("old", "value")
                .listenerAttributes(Map.of("new", "value"))
                .build();

        // then
        assertThat(options.listenerAttributes()).containsOnlyKeys("new");
    }

    @Test
    void should_reject_null_key() {
        assertThatThrownBy(() -> ChatRequestOptions.builder().addListenerAttribute(null, "v"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_reject_null_value() {
        assertThatThrownBy(() -> ChatRequestOptions.builder().addListenerAttribute("k", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_make_defensive_copy() {

        // given
        Map<Object, Object> original = new HashMap<>();
        original.put("key", "value");

        // when
        ChatRequestOptions options =
                ChatRequestOptions.builder().listenerAttributes(original).build();
        original.put("extra", "modified");

        // then
        assertThat(options.listenerAttributes()).doesNotContainKey("extra");
    }

    @Test
    void should_handle_null_bulk_map() {

        // when
        ChatRequestOptions options = ChatRequestOptions.builder()
                .addListenerAttribute("key", "value")
                .listenerAttributes(null)
                .build();

        // then
        assertThat(options.listenerAttributes()).containsEntry("key", "value");
    }

    @Test
    void should_have_correct_equals_and_hashCode() {

        // given
        ChatRequestOptions a =
                ChatRequestOptions.builder().addListenerAttribute("k", "v").build();
        ChatRequestOptions b =
                ChatRequestOptions.builder().addListenerAttribute("k", "v").build();

        // then
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void should_have_meaningful_toString() {
        ChatRequestOptions options = ChatRequestOptions.builder()
                .addListenerAttribute("tenantId", "acme")
                .build();

        assertThat(options.toString()).contains("tenantId").contains("acme");
    }
}
