package dev.langchain4j.jackson3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.scope.AgenticScopeRegistry;
import dev.langchain4j.agentic.scope.AgenticScopeSerializer;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.scope.UnserializableAgenticScopeException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.EventListener;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Agent state persistence in the same Jackson-2-free application, with
 * {@code langchain4j-agentic-json-jackson3} supplying the codec.
 *
 * <p>{@code langchain4j-agentic} carries a Jackson 2 codec of its own as the fallback. These pass
 * only if that fallback is genuinely never reached, since the classes it needs are not on the
 * classpath here.
 */
class AgenticScopeWithoutJackson2Test {

    private static DefaultAgenticScope scope() {
        return new AgenticScopeRegistry("test-agent").create("session-1");
    }

    @Test
    void state_round_trips() {
        DefaultAgenticScope scope = scope();
        scope.writeState("name", "Ada");
        scope.writeState("count", 36);
        scope.writeState("amount", new BigDecimal("12.50"));
        scope.writeState("tags", List.of("a", "b"));
        scope.writeState("nested", Map.of("k", "v"));

        DefaultAgenticScope restored = AgenticScopeSerializer.fromJson(AgenticScopeSerializer.toJson(scope));

        assertThat(restored.readState("name")).isEqualTo("Ada");
        assertThat(restored.readState("count")).isEqualTo(36);
        assertThat(restored.readState("amount")).isEqualTo(new BigDecimal("12.50"));
        assertThat(restored.readState("tags")).isEqualTo(List.of("a", "b"));
        assertThat(restored.readState("nested")).isEqualTo(Map.of("k", "v"));
    }

    @Test
    void the_memory_id_survives() {
        DefaultAgenticScope restored = AgenticScopeSerializer.fromJson(AgenticScopeSerializer.toJson(scope()));

        assertThat(restored.memoryId()).isEqualTo("session-1");
    }

    /** Reproduces #5285: the framework puts runtime proxies into state, and they must not break saving. */
    @Test
    void a_jdk_proxy_in_state_does_not_break_serialization() {
        DefaultAgenticScope scope = scope();
        scope.writeState("data", "serializable value");
        scope.writeState(
                "listener",
                Proxy.newProxyInstance(
                        getClass().getClassLoader(), new Class[] {EventListener.class}, (proxy, method, args) -> null));

        assertThatNoException().isThrownBy(() -> AgenticScopeSerializer.toJson(scope));
    }

    // ---------------------------------------------------------------
    // The allowlist. State is written with type information, so deserializing means
    // instantiating classes named in the document - the reason there is a list at all.
    // ---------------------------------------------------------------

    // Two types, because allowlist registration is process-wide and permanent: registering the
    // one below would otherwise make the refusal test depend on which test ran first.

    public static class NeverAllowed {

        public String sku;

        public NeverAllowed() {}

        public NeverAllowed(String sku) {
            this.sku = sku;
        }
    }

    public static class Allowed {

        public String sku;

        public Allowed() {}

        public Allowed(String sku) {
            this.sku = sku;
        }
    }

    @Test
    void an_unregistered_type_is_refused() {
        DefaultAgenticScope scope = scope();
        scope.writeState("order", new NeverAllowed("abc-1"));

        String json = AgenticScopeSerializer.toJson(scope);

        assertThatThrownBy(() -> AgenticScopeSerializer.fromJson(json))
                .isInstanceOf(UnserializableAgenticScopeException.class);
    }

    @Test
    void a_registered_type_is_accepted() {
        AgenticScopeSerializer.allowDeserializationType(Allowed.class);

        DefaultAgenticScope scope = scope();
        scope.writeState("order", new Allowed("abc-1"));

        DefaultAgenticScope restored = AgenticScopeSerializer.fromJson(AgenticScopeSerializer.toJson(scope));

        assertThat(restored.readState("order")).isInstanceOfSatisfying(Allowed.class, order -> assertThat(order.sku)
                .isEqualTo("abc-1"));
    }
}
