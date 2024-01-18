package dev.langchain4j.store.embedding.vearch.api.space;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class SpaceEngine {

    private Long indexSize;
    private RetrievalType retrievalType;
    private RetrievalParam retrievalParam;

    public void setRetrievalParam(RetrievalParam retrievalParam) {
        // do some constraint check
        Class<? extends RetrievalParam> clazz = retrievalType.getParamClass();
        if (clazz.isInstance(retrievalParam)) {
            throw new UnsupportedOperationException(
                    String.format("can't assign unknown param of engine %s, please use class %s to assign engine param",
                            retrievalType.name(), clazz.getSimpleName()));
        }
        this.retrievalParam = retrievalParam;
    }
}
