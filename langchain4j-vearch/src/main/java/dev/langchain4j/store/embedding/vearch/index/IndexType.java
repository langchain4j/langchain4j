package dev.langchain4j.store.embedding.vearch.index;

import dev.langchain4j.store.embedding.vearch.index.search.BINARYIVFSearchParam;
import dev.langchain4j.store.embedding.vearch.index.search.FLATSearchParam;
import dev.langchain4j.store.embedding.vearch.index.search.GPUSearchParam;
import dev.langchain4j.store.embedding.vearch.index.search.HNSWSearchParam;
import dev.langchain4j.store.embedding.vearch.index.search.IVFFLATSearchParam;
import dev.langchain4j.store.embedding.vearch.index.search.IVFPQSearchParam;
import dev.langchain4j.store.embedding.vearch.index.search.SearchIndexParam;

public enum IndexType {

    SCALAR(null, null),
    IVFPQ(IVFPQParam.class, IVFPQSearchParam.class),
    HNSW(HNSWParam.class, HNSWSearchParam.class),
    GPU(GPUParam.class, GPUSearchParam.class),
    IVFFLAT(IVFFLATParam.class, IVFFLATSearchParam.class),
    BINARYIVF(BINARYIVFParam.class, BINARYIVFSearchParam.class),
    FLAT(FLATParam.class, FLATSearchParam.class);

    private final Class<? extends IndexParam> createSpaceParamClass;
    private final Class<? extends SearchIndexParam> searchParamClass;

    IndexType(Class<? extends IndexParam> createSpaceParamClass,
              Class<? extends SearchIndexParam> searchParamClass) {
        this.createSpaceParamClass = createSpaceParamClass;
        this.searchParamClass = searchParamClass;
    }

    public Class<? extends IndexParam> getCreateSpaceParamClass() {
        return createSpaceParamClass;
    }

    public Class<? extends SearchIndexParam> getSearchParamClass() {
        return searchParamClass;
    }
}
