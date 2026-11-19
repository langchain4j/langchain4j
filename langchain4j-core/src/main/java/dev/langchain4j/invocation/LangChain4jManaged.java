package dev.langchain4j.invocation;

import java.util.Map;
import dev.langchain4j.Internal;
import org.jspecify.annotations.Nullable;

/**
 * A marker interface for components that are managed by LangChain4j framework.
 * <p>
 * Implementing this interface indicates that the component is internally managed by LangChain4j,
 * and doesn't require to be instatiated or passed around by the user or LLM.
 *
 * @since 1.8.0
 */
@Internal
public interface LangChain4jManaged {

    ThreadLocal<Map<Class<? extends LangChain4jManaged>, LangChain4jManaged>> CURRENT = new ThreadLocal<>();

    static void setCurrent(Map<Class<? extends LangChain4jManaged>, LangChain4jManaged> current) {
        CURRENT.set(current);
    }

    static Map<Class<? extends LangChain4jManaged>, LangChain4jManaged> current() {
        return CURRENT.get();
    }

    static <T extends LangChain4jManaged> T current(Class<T> clazz) {
        var current = CURRENT.get();
        return current != null ? clazz.cast(current.get(clazz)) : null;
    }

    static void removeCurrent() {
        CURRENT.remove();
    }

    /**
     * Binds to the current thread the managed components it had before they were replaced, or none if it had none.
     * A component replacing them with its own for the time of a call can itself be called from inside another AI
     * service call, for instance by one of its tools: so it reads the ones of its caller first, with
     * {@link #current()}, and puts them back with this method when it is done, instead of leaving the thread
     * without any.
     *
     * @param previous the components returned by {@link #current()} before the replacement, or {@code null} if it
     * returned none, in which case the thread is left without any
     */
    static void restore(@Nullable Map<Class<? extends LangChain4jManaged>, LangChain4jManaged> previous) {
        if (previous == null) {
            removeCurrent();
        } else {
            setCurrent(previous);
        }
    }
}
