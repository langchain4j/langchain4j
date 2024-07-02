package dev.langchain4j.store.cassio;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

/**
 * Default Constructor.
 */
@Data
@AllArgsConstructor
public class ClusteredRecord {

    /** Partition id. */
    String partitionId;

    /** Row identifier. */
    UUID rowId;

    /** Text body. */
    String body;

    /**
     * Record for a clustered table.
     */
    public ClusteredRecord() {}

}