package dev.langchain4j.mcp.registryclient;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The registry model classes are read by whichever wire codec is configured, and a codec finds
 * properties through accessors rather than through private fields. A field with no public getter is
 * therefore not an error anywhere - it simply reads as null, which is how the {@code is*} flags on
 * these classes came to be silently false under Jackson 3 before they were fixed.
 *
 * <p>So the rule is: a private field is either reachable through a public {@code getX}/{@code isX},
 * or it names itself with {@code @JsonProperty}. This checks that, rather than leaving it to hold
 * by luck.
 */
class McpRegistryModelTest {

    private static final String PACKAGE = "dev/langchain4j/mcp/registryclient/model";

    @Test
    void every_field_is_reachable_by_a_codec() throws Exception {
        List<Class<?>> models = modelClasses();

        assertThat(models)
                .as("model classes found under %s - scanning is broken if this is empty", PACKAGE)
                .hasSizeGreaterThanOrEqualTo(15);

        List<String> unreachable = new ArrayList<>();
        for (Class<?> model : models) {
            for (Field field : model.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                if (field.isAnnotationPresent(JsonProperty.class) || hasPublicAccessor(model, field)) {
                    continue;
                }
                unreachable.add(model.getSimpleName() + "." + field.getName());
            }
        }

        assertThat(unreachable)
                .as("fields a codec cannot see - give each a public getter or a @JsonProperty")
                .isEmpty();
    }

    private static boolean hasPublicAccessor(Class<?> model, Field field) {
        String capitalized = Character.toUpperCase(field.getName().charAt(0)) + field.getName().substring(1);
        for (String name : List.of("get" + capitalized, "is" + capitalized, field.getName())) {
            try {
                Method accessor = model.getMethod(name);
                if (accessor.getParameterCount() == 0 && !accessor.getReturnType().equals(void.class)) {
                    return true;
                }
            } catch (NoSuchMethodException ignored) {
                // try the next spelling
            }
        }
        return false;
    }

    private static List<Class<?>> modelClasses() throws Exception {
        Path root = Path.of(
                McpRegistryJson.class.getProtectionDomain().getCodeSource().getLocation().toURI());

        List<Class<?>> classes = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root.resolve(PACKAGE))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                String name = root.relativize(file)
                        .toString()
                        .replace(java.io.File.separatorChar, '.')
                        .replaceAll("\\.class$", "");
                Class<?> type = Class.forName(name);
                // Nested classes here are fluent builders, which are never read from the wire
                if (!type.isInterface() && !type.isEnum() && type.getEnclosingClass() == null) {
                    classes.add(type);
                }
            }
        }
        return classes;
    }
}
