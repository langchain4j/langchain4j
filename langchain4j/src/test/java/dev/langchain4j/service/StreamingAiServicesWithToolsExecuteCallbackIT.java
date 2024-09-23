package dev.langchain4j.service;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.tool.ToolExecution;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

class StreamingAiServicesWithToolsExecuteCallbackIT {



    static class Tools {



        @Tool
        int add(int a, int b)  {
            System.out.println(String.format("hit tool add %d + %d =%d " ,a,b,a+b));
            return a+b;

        }
    }
    interface Assistant {

        TokenStream chat(String userMessage);
    }


    @Test
    void should_execute_a_tool_then_stream_tool_execute_callback() throws Exception {


        // TODO test more models
        StreamingChatLanguageModel streamingChatModel = OpenAiStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();



        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatLanguageModel(streamingChatModel)
                .tools(new Tools())
                .build();


        CompletableFuture<Response<AiMessage>> futureResponse = new CompletableFuture<>();

        String userMessage = "what is 1+2";

        List<ToolExecution> toolExecutionList= new ArrayList<>();

        assistant.chat(userMessage)
                .onNext(System.out::println)
                .onComplete(response -> {

                    futureResponse.complete(response);
                })
                .onToolExecuted(toolExecution -> {
                    toolExecutionList.add(toolExecution);
                })
                .onError(throwable -> futureResponse.completeExceptionally(throwable))
                .start();

        futureResponse.get(30, SECONDS);
        assertThat(toolExecutionList.size()).isEqualTo(1);
        assertThat(toolExecutionList.get(0).request().name()).isEqualTo("add");








    }





}
