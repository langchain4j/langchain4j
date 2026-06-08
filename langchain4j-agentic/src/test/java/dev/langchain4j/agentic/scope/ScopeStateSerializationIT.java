package dev.langchain4j.agentic.scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import dev.langchain4j.agentic.JsonInMemoryAgenticScopeStore;
import java.lang.reflect.Proxy;
import java.util.EventListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Reproduces <a href="https://github.com/langchain4j/langchain4j/issues/5285">#5285</a>:
 * DefaultAgenticScope.state can contain JDK dynamic proxy objects injected by
 * the framework at runtime (e.g. event listeners, AiServiceTokenStream).
 * These proxies are not serializable, so AgenticScopeSerializer.toJson() fails
 * when a custom AgenticScopeStore tries to persist the scope.
 *
 * <p>These tests will FAIL until the issue is fixed. The expected behavior is that
 * non-serializable runtime objects in state are either excluded from serialization
 * or separated from the serializable state.
 */
class ScopeStateSerializationIT {

    @AfterEach
    void cleanup() {
        AgenticScopePersister.setStore(null);
    }

    @Test
    void serialization_should_handle_jdk_dynamic_proxy_in_state() {
        DefaultAgenticScope scope = new DefaultAgenticScope("test-session", DefaultAgenticScope.Kind.PERSISTENT);

        scope.writeState("data", "serializable value");

        // The framework internally populates state with runtime objects that include
        // JDK dynamic proxies (e.g. event listeners wrapped by Proxy.newProxyInstance).
        // Simulate this by creating a proxy identical to what the framework produces.
        Object proxyListener = Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[]{EventListener.class},
                (proxy, method, args) -> null);
        scope.writeState("result", proxyListener);

        // Serialization should succeed, either by skipping non-serializable entries
        // or by separating runtime objects from the serializable state
        assertThatNoException().isThrownBy(() -> AgenticScopeSerializer.toJson(scope));
    }

    @Test
    void scope_store_save_should_handle_jdk_dynamic_proxy_in_state() {
        JsonInMemoryAgenticScopeStore store = new JsonInMemoryAgenticScopeStore();

        DefaultAgenticScope scope = new DefaultAgenticScope("test-session", DefaultAgenticScope.Kind.PERSISTENT);

        scope.writeState("data", "serializable value");

        Object proxyListener = Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[]{EventListener.class},
                (proxy, method, args) -> null);
        scope.writeState("result", proxyListener);

        // A custom AgenticScopeStore.save() should not blow up because the framework
        // put non-serializable runtime objects into the scope state
        AgenticScopeKey key = new AgenticScopeKey("test-agent", "test-session");
        assertThatNoException().isThrownBy(() -> store.save(key, scope));
    }

    @Test
    void persistent_scope_flush_should_handle_jdk_dynamic_proxy_in_state() {
        JsonInMemoryAgenticScopeStore store = new JsonInMemoryAgenticScopeStore();
        AgenticScopePersister.setStore(store);

        AgenticScopeRegistry registry = new AgenticScopeRegistry("test-agent");

        // Registry creates a PERSISTENT scope because a store is set
        DefaultAgenticScope scope = registry.create("test-session");

        scope.writeState("data", "serializable value");

        Object proxyListener = Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[]{EventListener.class},
                (proxy, method, args) -> null);
        scope.writeState("result", proxyListener);

        // rootCallEnded triggers flush → registry.update → store.save → toJson
        // This should not fail due to non-serializable runtime objects in state
        assertThatNoException().isThrownBy(() -> scope.rootCallEnded(registry, null));

        assertThat(store.getAllKeys()).isNotEmpty();
    }
}
