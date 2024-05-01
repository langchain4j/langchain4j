package dev.langchain4j.store.embedding.pgvector;

import dev.langchain4j.internal.ValidationUtils;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MetadataColumDefinition used to define column definition from sql String
 */
@Getter
public class MetadataColumDefinition {
    private final String fullDefinition;
    private final String name;
    private final String type;

    private MetadataColumDefinition(String fullDefinition, String name, String type) {
        this.fullDefinition = fullDefinition;
        this.name = name;
        this.type = type;
    }

    /**
     * transform sql string to MetadataColumDefinition
     * @param sqlDefinition sql definition string
     * @return MetadataColumDefinition
     */
    public static MetadataColumDefinition from(String sqlDefinition) {
       String fullDefinition = ValidationUtils.ensureNotNull(sqlDefinition, "Metadata column definition");
       List<String> tokens = Arrays.stream(fullDefinition.split(" "))
               .filter(s -> !s.isEmpty()).collect(Collectors.toList());
       if (tokens.size() < 2) {
           throw new IllegalArgumentException("Definition format should be: column type" +
                   " [ NULL | NOT NULL ] [ UNIQUE ] [ DEFAULT value ]");
       }
       String name = tokens.get(0);
       String type = tokens.get(1).toLowerCase();
       return new MetadataColumDefinition(fullDefinition, name, type);
    }
}
