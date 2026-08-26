package dev.langchain4j.agentic.scope;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import dev.langchain4j.Internal;

/**
 * Adapts {@link AgenticScopeTypeAllowlist} to Jackson 2. The decision lives on the allowlist so
 * that every codec applies the same one.
 */
@Internal
class ConfigurablePolymorphicTypeValidator extends PolymorphicTypeValidator.Base {

    private static final long serialVersionUID = 1L;

    private final AgenticScopeTypeAllowlist allowlist = AgenticScopeTypeAllowlist.INSTANCE;

    void addAllowedPrefix(String prefix) {
        allowlist.addAllowedPrefix(prefix);
    }

    void addAllowedClass(String className) {
        allowlist.addAllowedClass(className);
    }

    @Override
    public Validity validateBaseType(MapperConfig<?> config, JavaType baseType) {
        return Validity.INDETERMINATE;
    }

    @Override
    public Validity validateSubClassName(MapperConfig<?> config, JavaType baseType, String subClassName) {
        return allowlist.isAllowedTypeId(subClassName) ? Validity.ALLOWED : Validity.INDETERMINATE;
    }

    @Override
    public Validity validateSubType(MapperConfig<?> config, JavaType baseType, JavaType subType) {
        return allowlist.isAlwaysAllowedType(subType.getRawClass()) ? Validity.ALLOWED : Validity.DENIED;
    }
}
