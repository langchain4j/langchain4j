package dev.langchain4j.store.cassio;

import lombok.Data;

/**
 * Item Retrieved by the search.
 *
 * @param <EMBEDDED>
 *       record.
 */
@Data
public class AnnResult<EMBEDDED> {

    /**
     * Embedded object
     */
    private EMBEDDED embedded;

    /**
     * Score
     */
    private float similarity;

    /**
     * Default constructor.
     */
    public AnnResult() {}

}
