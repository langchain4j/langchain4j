package dev.langchain4j.internal;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

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

/**
 * Detection and naming for polymorphic base types — sealed interfaces / classes (no annotations
 * needed) and types annotated with Jackson's {@link JsonSubTypes}. Used by both schema generation
 * and the Jackson polymorphic-dispatch hook for sealed types.
 */
@Internal
public final class PolymorphicTypes {

    /** Default discriminator property name when {@code @JsonTypeInfo} is not configured. */
    public static final String DEFAULT_DISCRIMINATOR_PROPERTY = "type";

    private PolymorphicTypes() {}

    /**
     * Returns {@code true} if {@code type} is a polymorphic base — sealed, or annotated with
     * {@link JsonSubTypes} — and has at least one concrete subtype discoverable by langchain4j.
     */
    public static boolean isPolymorphic(Class<?> type) {
        if (type == null || type.isPrimitive() || type.isEnum() || type.isArray()) {
            return false;
        }
        if (type.getAnnotation(JsonSubTypes.class) != null || type.isSealed()) {
            return !findConcreteSubtypes(type).isEmpty();
        }
        return false;
    }

    /**
     * Concrete (instantiable) subtypes for {@code type}. Sealed hierarchies are flattened
     * recursively; {@code @JsonSubTypes} on the base takes precedence over sealed permits.
     */
    public static List<Class<?>> findConcreteSubtypes(Class<?> type) {
        Set<Class<?>> result = new LinkedHashSet<>();
        flatten(type, result);
        return new ArrayList<>(result);
    }

    private static void flatten(Class<?> type, Set<Class<?>> result) {
        JsonSubTypes ann = type.getAnnotation(JsonSubTypes.class);
        if (ann != null) {
            for (JsonSubTypes.Type t : ann.value()) {
                flatten(t.value(), result);
            }
            return;
        }
        if (type.isSealed()) {
            for (Class<?> permitted : type.getPermittedSubclasses()) {
                flatten(permitted, result);
            }
            return;
        }
        if (!type.isInterface() && !Modifier.isAbstract(type.getModifiers())) {
            result.add(type);
        }
    }

    /**
     * Discriminator property name. Honors {@code @JsonTypeInfo(property=...)} when explicit;
     * otherwise {@code "@type"} for {@code @JsonTypeInfo}-annotated bases (Jackson's default) and
     * {@link #DEFAULT_DISCRIMINATOR_PROPERTY} otherwise.
     */
    public static String discriminatorPropertyName(Class<?> baseType) {
        JsonTypeInfo ann = baseType.getAnnotation(JsonTypeInfo.class);
        if (ann == null) {
            return DEFAULT_DISCRIMINATOR_PROPERTY;
        }
        return isNullOrBlank(ann.property()) ? "@type" : ann.property();
    }

    /**
     * Discriminator value for a subtype. Resolution order: {@code @JsonSubTypes.Type(name=...)}
     * on the base → {@code @JsonTypeName} on the subtype → {@link Class#getSimpleName()}.
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
        JsonTypeName typeName = subtype.getAnnotation(JsonTypeName.class);
        if (typeName != null && !isNullOrBlank(typeName.value())) {
            return typeName.value();
        }
        return subtype.getSimpleName();
    }

    /**
     * Verifies that the {@code @JsonTypeInfo} configuration on {@code baseType} (if present) uses
     * settings supported by langchain4j. Throws otherwise so the user gets a clear failure at
     * schema-generation time rather than a silent mismatch.
     *
     * <p>Supported {@code use}: {@code Id.NAME}, {@code Id.SIMPLE_NAME}.<br>
     * Supported {@code include}: {@code As.PROPERTY}, {@code As.EXISTING_PROPERTY}.</p>
     */
    public static void verifyJsonTypeInfoIsSupported(Class<?> baseType) {
        JsonTypeInfo ann = baseType.getAnnotation(JsonTypeInfo.class);
        if (ann == null) {
            return;
        }
        if (ann.use() != JsonTypeInfo.Id.NAME && ann.use() != JsonTypeInfo.Id.SIMPLE_NAME) {
            throw new UnsupportedFeatureException(String.format(
                    "@JsonTypeInfo(use = Id.%s) on %s is not supported for AI Service return types. "
                            + "Supported values: Id.NAME, Id.SIMPLE_NAME.",
                    ann.use().name(), baseType.getName()));
        }
        if (ann.include() != JsonTypeInfo.As.PROPERTY && ann.include() != JsonTypeInfo.As.EXISTING_PROPERTY) {
            throw new UnsupportedFeatureException(String.format(
                    "@JsonTypeInfo(include = As.%s) on %s is not supported for AI Service return types. "
                            + "Supported values: As.PROPERTY, As.EXISTING_PROPERTY.",
                    ann.include().name(), baseType.getName()));
        }
    }
}
