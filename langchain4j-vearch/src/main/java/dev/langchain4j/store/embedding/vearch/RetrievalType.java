package dev.langchain4j.store.embedding.vearch;

import lombok.Getter;

public enum RetrievalType {

    IVFPQ(RetrievalParam.IVFPQParam.class),
    HNSW(RetrievalParam.HNSWParam.class),
    GPU(RetrievalParam.GPUParam.class),
    IVFFLAT(RetrievalParam.IVFFLATParam.class),
    BINARYIVF(RetrievalParam.BINARYIVFParam.class),
    FLAT(RetrievalParam.FLAT.class);

    @Getter
    private Class<? extends RetrievalParam> paramClass;

    RetrievalType(Class<? extends RetrievalParam> paramClass) {
        this.paramClass = paramClass;
    }
}
