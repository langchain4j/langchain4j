package dev.langchain4j.internal;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import dev.langchain4j.Internal;
import dev.langchain4j.exception.UnsupportedFeatureException;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

/**
 * Utilities for handling polymorphic types (sealed interfaces/classes, abstract classes/interfaces
 * annotated with {@link JsonSubTypes}) when generating JSON schemas and parsing structured outputs.
 */
@Internal
public class PolymorphicTypes {

    /**
     * Default discriminator property name used when no Jackson {@link JsonTypeInfo} is configured
     * on the polymorphic base type.
     */
    public static final String DEFAULT_DISCRIMINATOR_PROPERTY = "type";

    private PolymorphicTypes() {
    }

    /**
     * Verifies that the {@link JsonTypeInfo} configuration on {@code baseType} (if present) uses
     * settings supported by LangChain4j. Throws {@link UnsupportedFeatureException} otherwise so the
     * user gets a clear failure at schema-generation time rather than a silent mismatch between the
     * generated schema and Jackson's deserialization.
     *
     * <p>Supported {@code use} values: {@code Id.NAME}, {@code Id.SIMPLE_NAME}.<br>
     * Supported {@code include} value: {@code As.PROPERTY}.</p>
     */
    public static void verifyJsonTypeInfoIsSupported(Class<?> baseType) {
        JsonTypeInfo ann = baseType.getAnnotation(JsonTypeInfo.class);
        if (ann == null) {
            return;
        }
        JsonTypeInfo.Id use = ann.use();
        if (use != JsonTypeInfo.Id.NAME && use != JsonTypeInfo.Id.SIMPLE_NAME) {
            throw new UnsupportedFeatureException(String.format(
                    "@JsonTypeInfo(use = Id.%s) on %s is not supported for AI Service return types. "
                            + "Supported values: Id.NAME, Id.SIMPLE_NAME.",
                    use.name(), baseType.getName()));
        }
        if (ann.include() != JsonTypeInfo.As.PROPERTY) {
            throw new UnsupportedFeatureException(String.format(
                    "@JsonTypeInfo(include = As.%s) on %s is not supported for AI Service return types. "
                            + "Supported value: As.PROPERTY.",
                    ann.include().name(), baseType.getName()));
        }
    }

    /**
     * Returns {@code true} if {@code type} is a polymorphic base type for which concrete subtypes
     * can be discovered automatically — either a sealed interface/class, or any type annotated
     * with Jackson's {@link JsonSubTypes}.
     */
    public static boolean isPolymorphic(Class<?> type) {
        if (type == null || type.isPrimitive() || type.isEnum() || type.isArray()) {
            return false;
        }
        if (hasJsonSubTypes(type) || type.isSealed()) {
            return !findConcreteSubtypes(type).isEmpty();
        }
        return false;
    }

    /**
     * Returns the list of concrete (instantiable) subtypes for a polymorphic base type. Sealed
     * hierarchies are flattened recursively. {@link JsonSubTypes} declarations take precedence.
     */
    public static List<Class<?>> findConcreteSubtypes(Class<?> type) {
        Set<Class<?>> result = new LinkedHashSet<>();
        flattenSubtypes(type, result);
        return new ArrayList<>(result);
    }

    private static void flattenSubtypes(Class<?> type, Set<Class<?>> result) {
        if (hasJsonSubTypes(type)) {
            JsonSubTypes ann = type.getAnnotation(JsonSubTypes.class);
            for (JsonSubTypes.Type t : ann.value()) {
                flattenSubtypes(t.value(), result);
            }
            return;
        }
        if (type.isSealed()) {
            for (Class<?> permitted : type.getPermittedSubclasses()) {
                flattenSubtypes(permitted, result);
            }
            return;
        }
        if (!type.isInterface() && !Modifier.isAbstract(type.getModifiers())) {
            result.add(type);
        }
    }

    /**
     * Returns the discriminator property name to use when generating the schema and when parsing
     * the LLM response. Honors {@link JsonTypeInfo#property()} when set explicitly; otherwise,
     * when {@code @JsonTypeInfo} is present, returns Jackson's default ({@code "@type"}) for
     * {@code Id.NAME} / {@code Id.SIMPLE_NAME}; otherwise returns
     * {@link #DEFAULT_DISCRIMINATOR_PROPERTY}.
     */
    public static String discriminatorPropertyName(Class<?> baseType) {
        JsonTypeInfo ann = baseType.getAnnotation(JsonTypeInfo.class);
        if (ann != null) {
            if (!isNullOrBlank(ann.property())) {
                return ann.property();
            }
            return "@type";
        }
        return DEFAULT_DISCRIMINATOR_PROPERTY;
    }

    /**
     * Returns the discriminator value for a concrete subtype. Resolution order:
     * <ol>
     *   <li>{@code @JsonSubTypes.Type(name = "...")} on the base type</li>
     *   <li>{@code @JsonTypeName} on the subtype</li>
     *   <li>{@link Class#getSimpleName()}</li>
     * </ol>
     */
    public static String discriminatorValue(Class<?> baseType, Class<?> subtype) {
        JsonSubTypes ann = baseType.getAnnotation(JsonSubTypes.class);
        if (ann != null) {
            for (JsonSubTypes.Type t : ann.value()) {
                if (t.value() == subtype && !isNullOrBlank(t.name())) {
                    return t.name();
                }
            }
        }
        JsonTypeName name = subtype.getAnnotation(JsonTypeName.class);
        if (name != null && !isNullOrBlank(name.value())) {
            return name.value();
        }
        return subtype.getSimpleName();
    }

    /**
     * Resolves a discriminator value back to one of the concrete subtypes of {@code baseType}.
     * Returns {@code null} when no subtype matches.
     */
    public static Class<?> findSubtypeByDiscriminator(Class<?> baseType, String discriminator) {
        if (isNullOrBlank(discriminator)) {
            return null;
        }
        for (Class<?> subtype : findConcreteSubtypes(baseType)) {
            if (discriminator.equals(discriminatorValue(baseType, subtype))) {
                return subtype;
            }
        }
        return null;
    }

    private static boolean hasJsonSubTypes(Class<?> type) {
        return type.getAnnotation(JsonSubTypes.class) != null;
    }
}
