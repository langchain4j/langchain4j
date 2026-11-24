package dev.langchain4j.internal;

import dev.langchain4j.Internal;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Decides which types may be instantiated while reading JSON that carries type information.
 *
 * <p>JSON written with type information names the classes to instantiate, so reading it is a way to
 * instantiate whatever the document asks for. An allowlist is what keeps that from being a way to
 * instantiate anything on the classpath.
 *
 * <p>The decision is expressed here in terms of class names rather than in a JSON library's
 * validator interface, so that every codec asks the same question of the same list.
 */
@Internal
public class TypeAllowlist {

    private static final Set<Class<?>> ALLOWED_BASE_CLASSES =
            Set.of(Number.class, String.class, Boolean.class, Character.class, Enum.class);

    private final Set<String> allowedPrefixes = new CopyOnWriteArraySet<>();
    private final Set<String> allowedClasses = new CopyOnWriteArraySet<>();

    /**
     * Starts with the JDK value types and LangChain4j's own data types, which any document may
     * name. Everything else is up to the caller.
     */
    public TypeAllowlist() {
        allowedPrefixes.add("java.util.");
        allowedPrefixes.add("java.math.");
        allowedPrefixes.add("dev.langchain4j.data.message.");
        allowedPrefixes.add("dev.langchain4j.data.image.");
        allowedPrefixes.add("dev.langchain4j.data.audio.");
        allowedPrefixes.add("dev.langchain4j.data.video.");
        allowedPrefixes.add("dev.langchain4j.data.pdf.");
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
