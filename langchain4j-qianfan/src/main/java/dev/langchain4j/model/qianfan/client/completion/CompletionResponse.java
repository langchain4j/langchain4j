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
    private  Integer sentence_id;
    private  Boolean is_end;
    private  Boolean is_truncated;
    private  String result;
    private  String finish_reason;
    private  Boolean need_clear_history;
    private  Integer ban_round;
    private  Usage usage;
    private FunctionCall function_call;



}
