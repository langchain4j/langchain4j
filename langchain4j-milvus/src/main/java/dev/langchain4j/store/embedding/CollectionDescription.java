package dev.langchain4j.store.embedding;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CollectionDescription {

    private String collectionName;
    private String idFieldName;
    private String vectorFieldName;
    private String scalarFieldName;

}
