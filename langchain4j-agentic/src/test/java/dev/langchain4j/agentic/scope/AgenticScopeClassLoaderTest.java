package dev.langchain4j.agentic.scope;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.scope.domain.Order;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * An application whose domain types are not visible to the class loader that loaded LangChain4j -
 * a war in an application server, a bundle in OSGi - needs to say which loader resolves the type
 * names in a serialized {@link AgenticScope}.
 */
class AgenticScopeClassLoaderTest {

    /** Delegates to the loader that would have been used anyway, and records what was asked for. */
    static class RecordingClassLoader extends ClassLoader {

        final List<String> requested = new CopyOnWriteArrayList<>();

        RecordingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            requested.add(name);
            return super.loadClass(name);
        }
    }

    @AfterEach
    void restoreTheDefaultClassLoader() {
        // The setting is process-wide, so a test must not leave its own loader behind.
        AgenticScopeSerializer.withClassLoader(AgenticScopeClassLoaderTest.class.getClassLoader());
    }

    private static DefaultAgenticScope scopeHolding(Order order) {
        DefaultAgenticScope scope = new DefaultAgenticScope(DefaultAgenticScope.Kind.PERSISTENT);
        scope.writeState("order", order);
        return scope;
    }

    @Test
    void the_configured_class_loader_resolves_the_types_named_in_the_document() {
        AgenticScopeSerializer.allowDeserializationType(Order.class);
        RecordingClassLoader loader = new RecordingClassLoader(getClass().getClassLoader());
        AgenticScopeSerializer.withClassLoader(loader);

        String json = AgenticScopeSerializer.toJson(scopeHolding(new Order("abc-1")));
        loader.requested.clear();

        DefaultAgenticScope restored = AgenticScopeSerializer.fromJson(json);

        assertThat(restored.readState("order"))
                .isInstanceOfSatisfying(Order.class, order -> assertThat(order.getSku()).isEqualTo("abc-1"));
        assertThat(loader.requested)
                .as("the type named in the document was resolved through the configured loader")
                .contains(Order.class.getName());
    }

    @Test
    void state_still_round_trips_after_the_class_loader_is_changed() {
        AgenticScopeSerializer.allowDeserializationType(Order.class);
        AgenticScopeSerializer.withClassLoader(new RecordingClassLoader(getClass().getClassLoader()));

        DefaultAgenticScope scope = scopeHolding(new Order("abc-2"));
        DefaultAgenticScope restored = AgenticScopeSerializer.fromJson(AgenticScopeSerializer.toJson(scope));

        assertThat(restored.memoryId()).isEqualTo(scope.memoryId());
        assertThat(restored.readState("order"))
                .isInstanceOfSatisfying(Order.class, order -> assertThat(order.getSku()).isEqualTo("abc-2"));
    }

    @Test
    void registering_the_package_of_a_type_allows_it_and_sets_the_loader() {
        // Order sits in a package of its own, so allowing that package widens nothing else.
        AgenticScopeSerializer.registerForDeserializationPackageOf(Order.class);

        String json = AgenticScopeSerializer.toJson(scopeHolding(new Order("abc-3")));

        assertThat(AgenticScopeSerializer.fromJson(json).readState("order"))
                .isInstanceOfSatisfying(Order.class, order -> assertThat(order.getSku()).isEqualTo("abc-3"));
    }
}
