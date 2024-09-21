package dev.langchain4j.model.voyage;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;

import java.util.List;

/**
 * An implementation of a {@link ScoringModel} that uses
 * <a href="https://docs.voyageai.com/docs/reranker">Voyage AI Rerank API</a>.
 */
public class VoyageScoringModel implements ScoringModel {



    @Override
    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
        return null;
    }
}
