package dev.langchain4j.model.output;

import lombok.Builder;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

import static dev.langchain4j.internal.Utils.setOf;

/**
 * Parses standard Java enums.
 * @param <T> the enum type
 */
@RequiredArgsConstructor(staticName = "forClass")
@Builder
public class EnumOutputParser<T extends Enum<?>> implements TextOutputParser<T> {
    private final Class<T> enumClass;

    @Override
    public T parse(String string) {
        for (T enumConstant : enumClass.getEnumConstants()) {
            if (enumConstant.name().equalsIgnoreCase(string)) {
                return enumConstant;
            }
        }
        throw new RuntimeException("Unknown enum value: " + string);
    }

    @Override
    public Set<Class<?>> getSupportedTypes() {
        return setOf(enumClass);
    }

    @Override
    public String formatInstructions() {
        return "one of " + Arrays.toString(enumClass.getEnumConstants());
    }

    public static Factory factory() {
        return new Factory();
    }

    public static class Factory implements ParserFactory {
        @SuppressWarnings("unchecked")
        @Override
        public Optional<OutputParser<?>> create(final TypeInformation typeInformation, final ParserProvider parserProvider) {
            if (!typeInformation.getRawType().isEnum()) {
                return Optional.empty();
            }
            return Optional.of(EnumOutputParser.builder()
                    .enumClass((Class<Enum<?>>) typeInformation.getRawType())
                    .build());
        }
    }
}
