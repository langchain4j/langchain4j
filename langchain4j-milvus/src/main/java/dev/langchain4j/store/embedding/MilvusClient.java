package dev.langchain4j.store.embedding;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import lombok.Getter;
import lombok.NonNull;

@Getter
public class MilvusClient extends MilvusServiceClient {


    public MilvusClient(@NonNull ConnectParam connectParam) {
        super(connectParam);
    }


}
