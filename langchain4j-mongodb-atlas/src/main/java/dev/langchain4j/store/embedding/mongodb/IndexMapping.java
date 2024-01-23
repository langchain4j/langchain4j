package dev.langchain4j.store.embedding.mongodb;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndexMapping {

    private int dimension;
    private Set<String> metadataFieldNames;

    public static IndexMapping defaultIndexMapping() {
        Set<String> metadataFieldNames = new HashSet<>(1);
        metadataFieldNames.add("type");
        return IndexMapping.builder()
                .dimension(384)
                .metadataFieldNames(metadataFieldNames)
                .build();
    }
}
