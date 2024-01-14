package dev.langchain4j.store.embedding.vearch.api.space;

public class SpaceEngine {

    private Long indexSize;
    private String idType;
    private RetrievalType retrievalType;
    private RetrievalParam retrievalParam;

    public Long getIndexSize() {
        return indexSize;
    }

    public void setIndexSize(Long indexSize) {
        this.indexSize = indexSize;
    }

    public String getIdType() {
        return idType;
    }

    public void setIdType(String idType) {
        this.idType = idType;
    }

    public RetrievalType getRetrievalType() {
        return retrievalType;
    }

    public void setRetrievalType(RetrievalType retrievalType) {
        this.retrievalType = retrievalType;
    }

    public RetrievalParam getRetrievalParam() {
        return retrievalParam;
    }

    public void setRetrievalParam(RetrievalParam retrievalParam) {
        Class<? extends RetrievalParam> clazz = retrievalType.getParamClass();
        if (clazz.isInstance(retrievalParam)) {
            throw new UnsupportedOperationException(
                    String.format("can't assign unknown param of engine %s, please use class %s to assign engine param",
                            retrievalType.name(), clazz.getSimpleName()));
        }
        this.retrievalParam = retrievalParam;
    }
}
