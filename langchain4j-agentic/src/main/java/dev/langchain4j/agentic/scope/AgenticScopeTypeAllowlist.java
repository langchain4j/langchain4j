package dev.langchain4j.agentic.scope;

import dev.langchain4j.Internal;
import dev.langchain4j.agentic.internal.PendingResponse;
import dev.langchain4j.agentic.internal.SuspendedResponse;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Decides which types may be instantiated while deserializing an {@link AgenticScope}'s state.
 *
 * <p>{@code AgenticScope} state is written with type information, so deserializing it means
 * instantiating classes named in the document. This allowlist is what keeps that from being a way
 * to instantiate anything on the classpath.
 *
 * <p>The decision is expressed here, in terms of class names, rather than in a JSON library's
 * validator interface, so that every codec asks the same question of the same list. There is one
 * instance per JVM: an application registers a type once and it holds whichever codec is active.
 */
@Internal
public final class AgenticScopeTypeAllowlist {

    public static final AgenticScopeTypeAllowlist INSTANCE = new AgenticScopeTypeAllowlist();

    private static final Set<Class<?>> ALLOWED_BASE_CLASSES =
            Set.of(Number.class, String.class, Boolean.class, Character.class, Enum.class);

    private final Set<String> allowedPrefixes = new CopyOnWriteArraySet<>();
    private final Set<String> allowedClasses = new CopyOnWriteArraySet<>();

    private AgenticScopeTypeAllowlist() {
        allowedPrefixes.add("java.util.");
        allowedPrefixes.add("java.math.");
        allowedPrefixes.add("dev.langchain4j.data.message.");
        allowedPrefixes.add("dev.langchain4j.data.image.");
        allowedPrefixes.add("dev.langchain4j.data.audio.");
        allowedPrefixes.add("dev.langchain4j.data.video.");
        allowedPrefixes.add("dev.langchain4j.data.pdf.");

        allowedClasses.add(DefaultAgenticScope.AgentMessage.class.getName());
        allowedClasses.add(AgentInvocation.class.getName());
        allowedClasses.add(PendingResponse.class.getName());
        allowedClasses.add(SuspendedResponse.class.getName());
    }

    public void addAllowedPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            throw new IllegalArgumentException("prefix must not be null or empty");
        }
        allowedPrefixes.add(prefix);
    }

    public void addAllowedClass(String className) {
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("className must not be null or empty");
        }
        allowedClasses.add(className);
    }

    /**
     * Whether a type named in the document may be instantiated. An array type is judged by its
     * element type.
     */
    public boolean isAllowedTypeId(String typeId) {
        String elementName = arrayElementName(typeId);
        if (elementName == null) {
            return true;
        }
        if (allowedClasses.contains(elementName)) {
            return true;
        }
        for (String prefix : allowedPrefixes) {
            if (elementName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a resolved type is one of the few always permitted regardless of the allowlist:
     * primitives, boxed primitives, strings and enums.
     */
    public boolean isAlwaysAllowedType(Class<?> rawClass) {
        Class<?> type = rawClass;
        while (type.isArray()) {
            type = type.getComponentType();
        }
        if (type.isPrimitive()) {
            return true;
        }
        for (Class<?> allowed : ALLOWED_BASE_CLASSES) {
            if (allowed.isAssignableFrom(type)) {
                return true;
            }
        }
        return false;
    }

    private static String arrayElementName(String typeId) {
        String name = typeId;
        while (name.startsWith("[")) {
            name = name.substring(1);
        }
        if (name.length() == typeId.length()) {
            return name;
        }
        if (name.length() <= 1) {
            return null;
        }
        if (name.startsWith("L") && name.endsWith(";")) {
            return name.substring(1, name.length() - 1);
        }
        return name;
    }
}
