package dev.langchain4j.output.parser.xml;

import static java.lang.String.format;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlCData;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import dev.langchain4j.model.output.structured.Description;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates XML format instructions from Java types.
 */
final class XmlSchemaGenerator {

    private static final String INDENT = "  ";
    private static final String DEFAULT_ITEM = "item";

    private XmlSchemaGenerator() {}

    /**
     * Generates format instructions for the given type.
     */
    static String generateFormatInstructions(Class<?> type) {
        String rootName = resolveRootName(type);
        String structure = generateStructure(type, rootName, 0, new HashSet<>());
        return """

                You must answer strictly in the following XML format:
                ```xml
                %s
                ```"""
                .formatted(structure);
    }

    /**
     * Generates format instructions for a collection type.
     */
    static String generateCollectionFormatInstructions(Class<?> elementType, String containerName, String itemName) {
        String itemStructure = generateStructure(elementType, itemName, 1, new HashSet<>());
        return """

                You must answer strictly in the following XML format:
                ```xml
                <%s>
                  <!-- Repeat the following element for each item -->
                %s
                </%s>
                ```"""
                .formatted(containerName, itemStructure, containerName);
    }

    private static String generateStructure(Class<?> type, String elementName, int depth, Set<Class<?>> visited) {
        StringBuilder sb = new StringBuilder();
        String indent = INDENT.repeat(depth);

        List<Field> attributes = new ArrayList<>();
        List<Field> elements = new ArrayList<>();
        classifyFields(type, attributes, elements);

        // Opening tag with attributes
        sb.append(indent).append("<").append(elementName);
        for (Field attr : attributes) {
            sb.append(format(" %s=\"(%s)\"", resolveFieldName(attr), typeDescription(attr)));
        }
        sb.append(">\n");

        // Attribute descriptions as comments
        for (Field attr : attributes) {
            appendDescription(sb, attr, indent + INDENT, "@" + resolveFieldName(attr));
        }

        visited.add(type);

        // Generate element content
        for (Field field : elements) {
            sb.append(generateFieldContent(field, depth + 1, visited));
        }

        sb.append(indent).append("</").append(elementName).append(">");

        return sb.toString();
    }

    private static String generateFieldContent(Field field, int depth, Set<Class<?>> visited) {
        StringBuilder sb = new StringBuilder();
        String indent = INDENT.repeat(depth);
        String name = resolveFieldName(field);
        Class<?> type = field.getType();

        appendDescription(sb, field, indent, null);

        if (isCollection(field)) {
            sb.append(generateCollectionContent(field, depth, visited));
        } else if (isComplexType(type) && !visited.contains(type)) {
            sb.append(generateStructure(type, name, depth, new HashSet<>(visited)));
            sb.append("\n");
        } else {
            String typeDesc = typeDescription(field);
            boolean isCData = field.getAnnotation(JacksonXmlCData.class) != null;
            if (isCData) {
                sb.append(format("%s<%s><![CDATA[(%s)]]></%s>\n", indent, name, typeDesc, name));
            } else {
                sb.append(format("%s<%s>(%s)</%s>\n", indent, name, typeDesc, name));
            }
        }

        return sb.toString();
    }

    private static String generateCollectionContent(Field field, int depth, Set<Class<?>> visited) {
        StringBuilder sb = new StringBuilder();
        String indent = INDENT.repeat(depth);

        String wrapperName = resolveWrapperName(field);
        Class<?> elementType = getCollectionElementType(field);
        String itemName = resolveItemName(field, elementType);

        if (wrapperName != null) {
            sb.append(format("%s<%s>\n", indent, wrapperName));
            sb.append(format("%s%s<!-- Repeat for each item -->\n", indent, INDENT));
            sb.append(generateItemContent(elementType, itemName, depth + 1, visited));
            sb.append(format("%s</%s>\n", indent, wrapperName));
        } else {
            sb.append(format("%s<!-- Repeat for each item -->\n", indent));
            sb.append(generateItemContent(elementType, itemName, depth, visited));
        }

        return sb.toString();
    }

    private static String generateItemContent(Class<?> elementType, String itemName, int depth, Set<Class<?>> visited) {
        String indent = INDENT.repeat(depth);

        if (elementType != null && isComplexType(elementType) && !visited.contains(elementType)) {
            return generateStructure(elementType, itemName, depth, new HashSet<>(visited)) + "\n";
        } else {
            String hint = elementType != null ? typeHint(elementType) : "item";
            return format("%s<%s>(%s)</%s>\n", indent, itemName, hint, itemName);
        }
    }

    private static String typeHint(Class<?> type) {
        return switch (type.getName()) {
            case "int",
                    "java.lang.Integer",
                    "long",
                    "java.lang.Long",
                    "short",
                    "java.lang.Short",
                    "byte",
                    "java.lang.Byte",
                    "java.math.BigInteger" -> "integer";

            case "double", "java.lang.Double", "float", "java.lang.Float", "java.math.BigDecimal" -> "number";

            case "boolean", "java.lang.Boolean" -> "true or false";

            case "char", "java.lang.Character" -> "character";

            case "java.lang.String" -> "string";

            case "java.time.LocalDate", "java.util.Date" -> "YYYY-MM-DD";
            case "java.time.LocalTime" -> "HH:MM:SS";
            case "java.time.LocalDateTime" -> "YYYY-MM-DDTHH:MM:SS";
            case "java.time.Instant" -> "ISO-8601 timestamp";
            case "java.time.ZonedDateTime", "java.time.OffsetDateTime" -> "YYYY-MM-DDTHH:MM:SSZ";
            case "java.time.Duration" -> "ISO-8601 duration";
            case "java.time.Period" -> "ISO-8601 period";

            case "java.util.UUID" -> "UUID";
            case "java.net.URI" -> "URI";
            case "java.net.URL" -> "URL";

            default -> type.getSimpleName();
        };
    }

    private static String typeDescription(Field field) {
        Class<?> type = field.getType();
        Type genericType = field.getGenericType();

        if (type.isEnum()) {
            String options = Arrays.stream(type.getEnumConstants())
                    .map(e -> ((Enum<?>) e).name())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            return "one of: " + options;
        }

        if (genericType instanceof ParameterizedType pt) {
            Type[] args = pt.getActualTypeArguments();
            if ((List.class.isAssignableFrom(type) || Set.class.isAssignableFrom(type)) && args.length > 0) {
                if (args[0] instanceof Class<?> elemType) {
                    return "array of " + typeHint(elemType);
                }
            }
        }

        if (type.isArray()) {
            return "array of " + typeHint(type.getComponentType());
        }

        return typeHint(type);
    }

    private static void classifyFields(Class<?> type, List<Field> attributes, List<Field> elements) {
        for (Field field : type.getDeclaredFields()) {
            if (shouldSkip(field)) continue;

            JacksonXmlProperty prop = field.getAnnotation(JacksonXmlProperty.class);
            if (prop != null && prop.isAttribute()) {
                attributes.add(field);
            } else {
                elements.add(field);
            }
        }
    }

    private static boolean shouldSkip(Field field) {
        String name = field.getName();
        return name.equals("__$hits$__")
                || name.startsWith("this$")
                || Modifier.isStatic(field.getModifiers())
                || Modifier.isTransient(field.getModifiers());
    }

    private static boolean isCollection(Field field) {
        Class<?> type = field.getType();
        return List.class.isAssignableFrom(type) || Set.class.isAssignableFrom(type) || type.isArray();
    }

    private static boolean isComplexType(Class<?> type) {
        if (type.isPrimitive() || type.isEnum() || type.isArray()) return false;
        String pkg = type.getPackage() != null ? type.getPackage().getName() : "";
        return !pkg.startsWith("java.") && !pkg.startsWith("javax.");
    }

    private static Class<?> getCollectionElementType(Field field) {
        Type genericType = field.getGenericType();

        if (genericType instanceof ParameterizedType pt) {
            Type[] args = pt.getActualTypeArguments();
            if (args.length > 0 && args[0] instanceof Class<?>) {
                return (Class<?>) args[0];
            }
        }

        if (field.getType().isArray()) {
            return field.getType().getComponentType();
        }

        return null;
    }

    private static String resolveRootName(Class<?> type) {
        JacksonXmlRootElement root = type.getAnnotation(JacksonXmlRootElement.class);
        if (root != null && !root.localName().isEmpty()) {
            return root.localName();
        }
        return toKebabCase(type.getSimpleName());
    }

    private static String resolveFieldName(Field field) {
        JacksonXmlProperty prop = field.getAnnotation(JacksonXmlProperty.class);
        if (prop != null && !prop.localName().isEmpty()) {
            return prop.localName();
        }
        return toKebabCase(field.getName());
    }

    private static String resolveWrapperName(Field field) {
        JacksonXmlElementWrapper wrapper = field.getAnnotation(JacksonXmlElementWrapper.class);
        if (wrapper != null) {
            if (!wrapper.useWrapping()) return null;
            if (!wrapper.localName().isEmpty()) return wrapper.localName();
        }
        return resolveFieldName(field);
    }

    private static String resolveItemName(Field field, Class<?> elementType) {
        JacksonXmlProperty prop = field.getAnnotation(JacksonXmlProperty.class);
        if (prop != null && !prop.localName().isEmpty()) {
            return prop.localName();
        }
        if (elementType != null) {
            return toKebabCase(elementType.getSimpleName());
        }
        return DEFAULT_ITEM;
    }

    private static String toKebabCase(String name) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) result.append("-");
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private static void appendDescription(StringBuilder sb, Field field, String indent, String prefix) {
        Description desc = field.getAnnotation(Description.class);
        if (desc != null) {
            String text =
                    prefix != null ? prefix + ": " + String.join(" ", desc.value()) : String.join(" ", desc.value());
            sb.append(format("%s<!-- %s -->\n", indent, text));
        }
    }
}
