package dev.langchain4j.json.jackson3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.JsonTypeNotAllowedException;
import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.PolymorphicJson;
import dev.langchain4j.internal.TypeAllowlist;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * State written with type information has to survive a change of JSON library, because it is
 * persisted: agent state saved by an application on Jackson 2 must still load after it adds
 * {@code langchain4j-json-jackson3}, and vice versa - a rollback has to work too.
 *
 * <p>The fixture below was produced by the Jackson 2 codec. Jackson 3 must both read it and write
 * it back byte for byte.
 */
class Jackson3PolymorphicCompatibilityTest {

    private static final String WRITTEN_BY_JACKSON2 =
            "{\"text\":\"Ada\",\"count\":36,\"flag\":true,\"amount\":[\"java.math.BigDecimal\",12.50],"
                    + "\"list\":[\"java.util.ImmutableCollections$List12\",[\"a\",\"b\"]],"
                    + "\"map\":[\"java.util.LinkedHashMap\",{\"k\":\"v\"}],"
                    + "\"message\":[\"dev.langchain4j.data.message.UserMessage\","
                    + "{\"contents\":[\"java.util.Collections$UnmodifiableRandomAccessList\","
                    + "[{\"text\":\"hello\",\"type\":\"TEXT\"}]],\"type\":\"USER\"}],"
                    + "\"ai\":[\"dev.langchain4j.data.message.AiMessage\",{\"text\":\"hi\","
                    + "\"toolExecutionRequests\":[\"java.util.ImmutableCollections$ListN\",[]],"
                    + "\"attributes\":[\"java.util.ImmutableCollections$MapN\",{}],\"type\":\"AI\"}]}";

    private final Json.JsonCodec codec = PolymorphicJson.codec(new TypeAllowlist());

    private static Map<String, Object> state() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("text", "Ada");
        state.put("count", 36);
        state.put("flag", true);
        state.put("amount", new BigDecimal("12.50"));
        state.put("list", List.of("a", "b"));
        state.put("map", new LinkedHashMap<>(Map.of("k", "v")));
        state.put("message", UserMessage.from("hello"));
        state.put("ai", AiMessage.from("hi"));
        return state;
    }

    @Test
    void the_spi_resolves_to_the_jackson3_codec() {
        assertThat(codec).isInstanceOf(Jackson3PolymorphicJsonCodec.class);
    }

    @Test
    void writes_what_jackson2_wrote() {
        assertThat(codec.toJson(state())).isEqualTo(WRITTEN_BY_JACKSON2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void reads_what_jackson2_wrote() {
        Map<String, Object> restored = codec.fromJson(WRITTEN_BY_JACKSON2, LinkedHashMap.class);

        assertThat(restored.get("text")).isEqualTo("Ada");
        assertThat(restored.get("count")).isEqualTo(36);
        assertThat(restored.get("flag")).isEqualTo(true);
        assertThat(restored.get("amount")).isEqualTo(new BigDecimal("12.50"));
        assertThat(restored.get("list")).isEqualTo(List.of("a", "b"));
        assertThat(restored.get("map")).isEqualTo(Map.of("k", "v"));
        assertThat(restored.get("message")).isEqualTo(UserMessage.from("hello"));
        assertThat(restored.get("ai")).isEqualTo(AiMessage.from("hi"));
    }

    public static class NotAllowed {
        public String sku;
    }

    @Test
    void refuses_a_type_that_is_not_allowlisted() {
        String json = "{\"order\":[\"" + NotAllowed.class.getName() + "\",{\"sku\":\"abc\"}]}";

        assertThatThrownBy(() -> codec.fromJson(json, LinkedHashMap.class))
                .isInstanceOf(JsonTypeNotAllowedException.class)
                .satisfies(e -> assertThat(((JsonTypeNotAllowedException) e).typeId())
                        .isEqualTo(NotAllowed.class.getName()));
    }

    @Test
    void accepts_a_type_once_it_is_allowlisted() {
        TypeAllowlist allowlist = new TypeAllowlist();
        allowlist.addAllowedClass(NotAllowed.class.getName());
        String json = "{\"order\":[\"" + NotAllowed.class.getName() + "\",{\"sku\":\"abc\"}]}";

        Map<?, ?> restored = PolymorphicJson.codec(allowlist).fromJson(json, LinkedHashMap.class);

        assertThat(restored.get("order")).isInstanceOfSatisfying(NotAllowed.class, order -> assertThat(order.sku)
                .isEqualTo("abc"));
    }
}
