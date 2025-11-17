package dev.langchain4j.model.vertexai.gemini;

import static dev.langchain4j.model.vertexai.gemini.ContentsMapper.splitInstructionAndContent;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.vertexai.api.Content;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContentsMapperTest {

    @Test
    void should_split_instructions_and_other_messages() {

        // given
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(SystemMessage.from("You are a smart assistant"));
        msgs.add(UserMessage.from("Can you help me, please?"));
        msgs.add(AiMessage.from("Sure, how can I assist you?"));

        // when
        ContentsMapper.InstructionAndContent instructionAndContent = splitInstructionAndContent(msgs);
        Content inst = instructionAndContent.systemInstruction;
        List<Content> contents = instructionAndContent.contents;

        // then
        assertThat(inst.getPartsCount()).isEqualTo(1);
        assertThat(inst.getParts(0).getText()).isEqualTo("You are a smart assistant");
        assertThat(contents).hasSize(2);
        assertThat(contents.get(0).getPartsCount()).isEqualTo(1);
        assertThat(contents.get(0).getParts(0).getText()).isEqualTo("Can you help me, please?");
        assertThat(contents.get(1).getPartsCount()).isEqualTo(1);
        assertThat(contents.get(1).getParts(0).getText()).isEqualTo("Sure, how can I assist you?");
    }

    @Test
    void should_combine_tool_execution_message_in_single_contents() {
        // given
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(SystemMessage.from("You are a smart calculator"));
        msgs.add(UserMessage.from("Calculate 3+4 and compare the result with 5+6"));
        List<ToolExecutionRequest> twoRequests = new ArrayList<>();
        twoRequests.add(ToolExecutionRequest.builder()
                .name("add")
                .arguments("{\"a\": 3, \"b\": 4}")
                .build());
        twoRequests.add(ToolExecutionRequest.builder()
                .name("add")
                .arguments("{\"a\": 5, \"b\": 6}")
                .build());
        msgs.add(AiMessage.from(twoRequests));
        msgs.add(ToolExecutionResultMessage.from(null, "add", "{\"result\": \"7\"}"));
        msgs.add(ToolExecutionResultMessage.from(null, "add", "{\"result\": \"11\"}"));
        msgs.add(AiMessage.from("3+4 is smaller than 5+6"));

        // when
        ContentsMapper.InstructionAndContent instructionAndContent = splitInstructionAndContent(msgs);
        Content inst = instructionAndContent.systemInstruction;
        List<Content> contents = instructionAndContent.contents;

        // then
        assertThat(inst.getPartsCount()).isEqualTo(1);
        assertThat(inst.getParts(0).getText()).isEqualTo("You are a smart calculator");

        assertThat(contents).hasSize(4);

        assertThat(contents.get(0).getParts(0).getText()).isEqualTo("Calculate 3+4 and compare the result with 5+6");

        assertThat(contents.get(1).getRole()).isEqualTo("model");
        assertThat(contents.get(1).getPartsCount()).isEqualTo(2);
        assertThat(contents.get(1).getParts(0).getFunctionCall().getName()).isEqualTo("add");
        assertThat(contents.get(1).getParts(0).getFunctionCall().getArgs())
                .isEqualTo(Struct.newBuilder()
                        .putFields("a", Value.newBuilder().setNumberValue(3.0).build())
                        .putFields("b", Value.newBuilder().setNumberValue(4.0).build())
                        .build());
        assertThat(contents.get(1).getParts(1).getFunctionCall().getName()).isEqualTo("add");
        assertThat(contents.get(1).getParts(1).getFunctionCall().getArgs())
                .isEqualTo(Struct.newBuilder()
                        .putFields("a", Value.newBuilder().setNumberValue(5.0).build())
                        .putFields("b", Value.newBuilder().setNumberValue(6.0).build())
                        .build());

        assertThat(contents.get(2).getRole()).isEqualTo("user");
        assertThat(contents.get(2).getPartsCount()).isEqualTo(2);
        assertThat(contents.get(2).getParts(0).getFunctionResponse().getName()).isEqualTo("add");
        assertThat(contents.get(2)
                        .getParts(0)
                        .getFunctionResponse()
                        .getResponse()
                        .getFieldsMap()
                        .get("result")
                        .getStringValue())
                .isEqualTo("7");
        assertThat(contents.get(2).getParts(1).getFunctionResponse().getName()).isEqualTo("add");
        assertThat(contents.get(2)
                        .getParts(1)
                        .getFunctionResponse()
                        .getResponse()
                        .getFieldsMap()
                        .get("result")
                        .getStringValue())
                .isEqualTo("11");

        assertThat(contents.get(3).getRole()).isEqualTo("model");
        assertThat(contents.get(3).getPartsCount()).isEqualTo(1);
        assertThat(contents.get(3).getParts(0).getText()).isEqualTo("3+4 is smaller than 5+6");
    }

    @Test
    void should_handle_empty_message_list() {
        // given
        List<ChatMessage> msgs = new ArrayList<>();

        // when
        ContentsMapper.InstructionAndContent instructionAndContent = splitInstructionAndContent(msgs);

        // then
        assertThat(instructionAndContent.systemInstruction).isNull();
        assertThat(instructionAndContent.contents).isEmpty();
    }

    @Test
    void should_handle_only_system_message() {
        // given
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(SystemMessage.from("Process data efficiently"));

        // when
        ContentsMapper.InstructionAndContent instructionAndContent = splitInstructionAndContent(msgs);

        // then
        assertThat(instructionAndContent.systemInstruction).isNotNull();
        assertThat(instructionAndContent.systemInstruction.getParts(0).getText())
                .isEqualTo("Process data efficiently");
        assertThat(instructionAndContent.contents).isEmpty();
    }

    @Test
    void should_handle_only_user_message() {
        // given
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(UserMessage.from("Process this request"));

        // when
        ContentsMapper.InstructionAndContent instructionAndContent = splitInstructionAndContent(msgs);

        // then
        assertThat(instructionAndContent.systemInstruction).isNull();
        assertThat(instructionAndContent.contents).hasSize(1);
        assertThat(instructionAndContent.contents.get(0).getParts(0).getText()).isEqualTo("Process this request");
    }

    @Test
    void should_handle_multiple_system_messages() {
        // given
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(SystemMessage.from("First configuration"));
        msgs.add(SystemMessage.from("Second configuration"));
        msgs.add(UserMessage.from("Input data"));

        // when
        ContentsMapper.InstructionAndContent instructionAndContent = splitInstructionAndContent(msgs);

        // then
        assertThat(instructionAndContent.systemInstruction).isNotNull();
        assertThat(instructionAndContent.contents).hasSize(1);
        assertThat(instructionAndContent.contents.get(0).getParts(0).getText()).isEqualTo("Input data");
    }

    @Test
    void should_handle_consecutive_user_messages() {
        // given
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(UserMessage.from("First input"));
        msgs.add(UserMessage.from("Second input"));
        msgs.add(UserMessage.from("Third input"));

        // when
        ContentsMapper.InstructionAndContent instructionAndContent = splitInstructionAndContent(msgs);

        // then
        assertThat(instructionAndContent.systemInstruction).isNull();
        assertThat(instructionAndContent.contents).hasSize(3);
        assertThat(instructionAndContent.contents.get(0).getParts(0).getText()).isEqualTo("First input");
        assertThat(instructionAndContent.contents.get(1).getParts(0).getText()).isEqualTo("Second input");
        assertThat(instructionAndContent.contents.get(2).getParts(0).getText()).isEqualTo("Third input");
    }

    @Test
    void should_handle_consecutive_ai_messages() {
        // given
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(UserMessage.from("Request"));
        msgs.add(AiMessage.from("Initial output"));
        msgs.add(AiMessage.from("Additional output"));

        // when
        ContentsMapper.InstructionAndContent instructionAndContent = splitInstructionAndContent(msgs);

        // then
        assertThat(instructionAndContent.contents).hasSize(3);
        assertThat(instructionAndContent.contents.get(0).getParts(0).getText()).isEqualTo("Request");
        assertThat(instructionAndContent.contents.get(1).getParts(0).getText()).isEqualTo("Initial output");
        assertThat(instructionAndContent.contents.get(2).getParts(0).getText()).isEqualTo("Additional output");
    }

    @Test
    void should_handle_tool_execution_without_ai_message() {
        // given
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(UserMessage.from("Execute task"));
        msgs.add(ToolExecutionResultMessage.from(null, "process", "{\"status\": \"complete\"}"));

        // when
        ContentsMapper.InstructionAndContent instructionAndContent = splitInstructionAndContent(msgs);

        // then
        assertThat(instructionAndContent.contents).hasSize(2);
        assertThat(instructionAndContent.contents.get(0).getParts(0).getText()).isEqualTo("Execute task");
        assertThat(instructionAndContent
                        .contents
                        .get(1)
                        .getParts(0)
                        .getFunctionResponse()
                        .getName())
                .isEqualTo("process");
    }

    @Test
    void should_handle_single_tool_execution_request() {
        // given
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(UserMessage.from("Request"));
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("Request")
                .arguments("{\"type\": \"standard\"}")
                .build();
        msgs.add(AiMessage.from(request));

        // when
        ContentsMapper.InstructionAndContent instructionAndContent = splitInstructionAndContent(msgs);

        // then
        assertThat(instructionAndContent.contents).hasSize(2);
        assertThat(instructionAndContent
                        .contents
                        .get(1)
                        .getParts(0)
                        .getFunctionCall()
                        .getName())
                .isEqualTo("Request");
    }

    @Test
    void should_handle_tool_with_empty_arguments() {
        // given
        List<ChatMessage> msgs = new ArrayList<>();
        ToolExecutionRequest request =
                ToolExecutionRequest.builder().name("getStatus").arguments("{}").build();
        msgs.add(AiMessage.from(request));

        // when
        ContentsMapper.InstructionAndContent instructionAndContent = splitInstructionAndContent(msgs);

        // then
        assertThat(instructionAndContent.contents).hasSize(1);
        assertThat(instructionAndContent
                        .contents
                        .get(0)
                        .getParts(0)
                        .getFunctionCall()
                        .getName())
                .isEqualTo("getStatus");
        assertThat(instructionAndContent
                        .contents
                        .get(0)
                        .getParts(0)
                        .getFunctionCall()
                        .getArgs()
                        .getFieldsCount())
                .isEqualTo(0);
    }

    @Test
    void should_handle_tool_with_null_arguments() {
        // given
        List<ChatMessage> msgs = new ArrayList<>();
        ToolExecutionRequest request =
                ToolExecutionRequest.builder().name("fetchData").arguments(null).build();
        msgs.add(AiMessage.from(request));

        // when
        ContentsMapper.InstructionAndContent instructionAndContent = splitInstructionAndContent(msgs);

        // then
        assertThat(instructionAndContent.contents).hasSize(1);
        assertThat(instructionAndContent
                        .contents
                        .get(0)
                        .getParts(0)
                        .getFunctionCall()
                        .getName())
                .isEqualTo("fetchData");
    }
}
