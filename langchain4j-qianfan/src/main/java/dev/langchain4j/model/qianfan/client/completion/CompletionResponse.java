package dev.langchain4j.model.qianfan.client.completion;

import dev.langchain4j.model.qianfan.client.Usage;
import dev.langchain4j.model.qianfan.client.chat.FunctionCall;
import lombok.Builder;
import lombok.Data;
@Data
@Builder
public final class CompletionResponse {

    private  String id;
    private  Integer errorCode;
    private  String errorMsg;
    private  String object;
    private  Integer created;
    private  Integer sentenceId;
    private  Boolean isEnd;
    private  Boolean isTruncated;
    private  String result;
    private  String finishReason;
    private  Boolean needClearHistory;
    private  Integer banRound;
    private  Usage usage;
    private FunctionCall functionCall;



}
