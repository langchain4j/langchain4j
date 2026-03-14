package dev.langchain4j.model.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.MapEntry.entry;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ChatRequestOptionsTest {

    @Test
    void empty_should_have_no_attributes() {
        assertThat(ChatRequestOptions.EMPTY.listenerAttributes()).isEmpty();
    }

    @Test
    void builder_should_build_with_single_attribute() {

        // when
        ChatRequestOptions options = ChatRequestOptions.builder()
                .listenerAttribute("tenantId", "acme-co")
                .build();

        // then
        assertThat(options.listenerAttributes()).containsExactly(entry("tenantId", "acme-co"));
    }

    @Test
    void builder_should_build_with_map_of_attributes() {

        // when
        ChatRequestOptions options = ChatRequestOptions.builder()
                .listenerAttributes(Map.of("k1", "v1", "k2", "v2"))
                .build();

        // then
        assertThat(options.listenerAttributes()).containsOnly(entry("k1", "v1"), entry("k2", "v2"));
    }

    @Test
    void builder_listenerAttributes_should_replace_previous_values() {

        // when
        ChatRequestOptions options = ChatRequestOptions.builder()
                .listenerAttribute("old", "value")
                .listenerAttributes(Map.of("new", "value"))
                .build();

        // then
        assertThat(options.listenerAttributes()).containsExactly(entry("new", "value"));
    }

    @Test
    void builder_listenerAttributes_with_null_should_clear() {

        // when
        ChatRequestOptions options = ChatRequestOptions.builder()
                .listenerAttribute("key", "value")
                .listenerAttributes(null)
                .build();

        // then
        assertThat(options.listenerAttributes()).isEmpty();
    }

    @Test
    void builder_should_reject_null_key() {
        assertThatThrownBy(() -> ChatRequestOptions.builder().listenerAttribute(null, "value"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void builder_should_reject_null_value() {
        assertThatThrownBy(() -> ChatRequestOptions.builder().listenerAttribute("key", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void builder_should_reject_null_key_in_bulk_map() {

        // given
        Map<Object, Object> map = new java.util.HashMap<>();
        map.put(null, "value");

        // when/then
        assertThatThrownBy(() -> ChatRequestOptions.builder().listenerAttributes(map))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void builder_should_reject_null_value_in_bulk_map() {

        // given
        Map<Object, Object> map = new java.util.HashMap<>();
        map.put("key", null);

        // when/then
        assertThatThrownBy(() -> ChatRequestOptions.builder().listenerAttributes(map))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void built_options_should_be_unmodifiable() {

        // given
        ChatRequestOptions options =
                ChatRequestOptions.builder().listenerAttribute("k", "v").build();

        // when/then
        assertThatThrownBy(() -> options.listenerAttributes().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void builder_should_defensive_copy() {

        // given
        Map<Object, Object> original = new java.util.HashMap<>();
        original.put("k", "v");

        ChatRequestOptions options =
                ChatRequestOptions.builder().listenerAttributes(original).build();

        // when — mutate the original map after build
        original.put("extra", "data");

        // then — options should not be affected
        assertThat(options.listenerAttributes()).containsExactly(entry("k", "v"));
    }

    @Test
    void should_be_equal_when_same_attributes() {

        // given
        ChatRequestOptions options1 =
                ChatRequestOptions.builder().listenerAttribute("k", "v").build();
        ChatRequestOptions options2 =
                ChatRequestOptions.builder().listenerAttribute("k", "v").build();

        // then
        assertThat(options1).isEqualTo(options2);
        assertThat(options1.hashCode()).isEqualTo(options2.hashCode());
    }

    @Test
    void should_not_be_equal_when_different_attributes() {

        // given
        ChatRequestOptions options1 =
                ChatRequestOptions.builder().listenerAttribute("k", "v1").build();
        ChatRequestOptions options2 =
                ChatRequestOptions.builder().listenerAttribute("k", "v2").build();

        // then
        assertThat(options1).isNotEqualTo(options2);
    }

    @Test
    void empty_instances_should_be_equal() {
        assertThat(ChatRequestOptions.EMPTY)
                .isEqualTo(ChatRequestOptions.builder().build());
    }

    @Test
    void toString_should_contain_attributes() {

        // given
        ChatRequestOptions options =
                ChatRequestOptions.builder().listenerAttribute("key", "value").build();

        // then
        assertThat(options.toString())
                .contains("listenerAttributes=")
                .contains("key")
                .contains("value");
    }
}
