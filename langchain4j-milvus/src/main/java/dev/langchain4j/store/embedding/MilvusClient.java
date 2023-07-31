package dev.langchain4j.store.embedding;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;

public class MilvusClient extends MilvusServiceClient {


    public MilvusClient(ConnectParam connectParam) {
        super(connectParam);
    }


}
