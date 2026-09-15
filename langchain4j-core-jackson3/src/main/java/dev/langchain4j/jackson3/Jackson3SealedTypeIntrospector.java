package dev.langchain4j.jackson3;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import dev.langchain4j.internal.PolymorphicTypes;
import java.util.List;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.introspect.Annotated;
import tools.jackson.databind.introspect.NopAnnotationIntrospector;
import tools.jackson.databind.jsontype.NamedType;
import tools.jackson.databind.jsontype.impl.StdTypeResolverBuilder;

/**
 * Makes sealed interfaces and classes deserializable as polymorphic types without the user having
 * to add {@code @JsonTypeInfo} and {@code @JsonSubTypes}, by synthesizing the equivalent metadata.
 */
final class Jackson3SealedTypeIntrospector extends NopAnnotationIntrospector {

    @Override
    public Object findTypeResolverBuilder(MapperConfig<?> config, Annotated ann) {
        Class<?> raw = ann.getRawType();
        if (!shouldHandle(raw)) {
            return null;
        }
        JsonTypeInfo.Value typeInfo = JsonTypeInfo.Value.construct(
                JsonTypeInfo.Id.NAME,
                JsonTypeInfo.As.PROPERTY,
                PolymorphicTypes.discriminatorPropertyName(raw),
                null,
                false,
                Boolean.FALSE);
        return new StdTypeResolverBuilder().init(typeInfo, null);
    }

    @Override
    public List<NamedType> findSubtypes(MapperConfig<?> config, Annotated a) {
        Class<?> raw = a.getRawType();
        if (!shouldHandle(raw)) {
            return null;
        }
        return PolymorphicTypes.findConcreteSubtypes(raw).stream()
                .map(sub -> new NamedType(sub, PolymorphicTypes.discriminatorValue(raw, sub)))
                .toList();
    }

    private static boolean shouldHandle(Class<?> raw) {
        // Step in for any polymorphic base that doesn't already declare its own type-info
        // strategy via @JsonTypeInfo. This covers both sealed types (no annotations) and
        // types that only use @JsonSubTypes for subtype enumeration.
        return raw.getAnnotation(JsonTypeInfo.class) == null && PolymorphicTypes.isPolymorphic(raw);
    }
}
