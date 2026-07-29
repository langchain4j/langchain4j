package dev.langchain4j.agentic.scope;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import dev.langchain4j.Internal;
import dev.langchain4j.agentic.internal.PendingResponse;
import dev.langchain4j.agentic.internal.SuspendedResponse;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Internal
class ConfigurablePolymorphicTypeValidator extends PolymorphicTypeValidator.Base {

    private static final long serialVersionUID = 1L;

    private static final Set<Class<?>> ALLOWED_BASE_CLASSES = Set.of(
            Number.class, String.class, Boolean.class, Character.class, Enum.class
    );

    private final Set<String> allowedPrefixes = new CopyOnWriteArraySet<>();
    private final Set<String> allowedClasses = new CopyOnWriteArraySet<>();

    ConfigurablePolymorphicTypeValidator() {
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

    void addAllowedPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            throw new IllegalArgumentException("prefix must not be null or empty");
        }
        allowedPrefixes.add(prefix);
    }

    void addAllowedClass(String className) {
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("className must not be null or empty");
        }
        allowedClasses.add(className);
    }

    @Override
    public Validity validateBaseType(MapperConfig<?> config, JavaType baseType) {
        return Validity.INDETERMINATE;
    }

    @Override
    public Validity validateSubClassName(MapperConfig<?> config, JavaType baseType, String subClassName) {
        String elementName = arrayElementName(subClassName);
        if (elementName == null) {
            return Validity.ALLOWED;
        }
        if (allowedClasses.contains(elementName)) {
            return Validity.ALLOWED;
        }
        for (String prefix : allowedPrefixes) {
            if (elementName.startsWith(prefix)) {
                return Validity.ALLOWED;
            }
        }
        return Validity.INDETERMINATE;
    }

    @Override
    public Validity validateSubType(MapperConfig<?> config, JavaType baseType, JavaType subType) {
        Class<?> rawClass = subType.getRawClass();
        while (rawClass.isArray()) {
            rawClass = rawClass.getComponentType();
        }
        if (rawClass.isPrimitive()) {
            return Validity.ALLOWED;
        }
        for (Class<?> allowed : ALLOWED_BASE_CLASSES) {
            if (allowed.isAssignableFrom(rawClass)) {
                return Validity.ALLOWED;
            }
        }
        return Validity.DENIED;
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
