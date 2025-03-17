package dev.langchain4j.store.embedding.vearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public enum RetrievalType {

    IVFPQ(RetrievalParam.IVFPQParam.class),
    HNSW(RetrievalParam.HNSWParam.class),
    GPU(RetrievalParam.GPUParam.class),
    IVFFLAT(RetrievalParam.IVFFLATParam.class),
    BINARYIVF(RetrievalParam.BINARYIVFParam.class),
    FLAT(RetrievalParam.FLAT.class);

    private final Class<? extends RetrievalParam> paramClass;

    RetrievalType(Class<? extends RetrievalParam> paramClass) {
        this.paramClass = paramClass;
    }

    public Class<? extends RetrievalParam> getParamClass() {
        return paramClass;
    }
}
