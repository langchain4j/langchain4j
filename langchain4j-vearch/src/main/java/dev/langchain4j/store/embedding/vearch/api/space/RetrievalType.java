package dev.langchain4j.store.embedding.vearch.api.space;

public enum RetrievalType {

    IVFPQ(RetrievalParam.IVFPQParam.class),
    HNSW(RetrievalParam.HNSWParam.class),
    GPU(RetrievalParam.GPUParam.class),
    IVFFLAT(RetrievalParam.IVFFLATParam.class),
    BINARYIVF(RetrievalParam.BINARYIVFParam.class);

    private Class<? extends RetrievalParam> paramClass;

    RetrievalType(Class<? extends RetrievalParam> paramClass) {
        this.paramClass = paramClass;
    }

    public Class<? extends RetrievalParam> getParamClass() {
        return paramClass;
    }
}
