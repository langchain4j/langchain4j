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

    @Test
    void should_handle_tool_result_as_json_object() {
        // given
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(ToolExecutionResultMessage.from(null, "getData", "{\"name\": \"John\", \"age\": 30}"));

        // when
        ContentsMapper.InstructionAndContent instructionAndContent = splitInstructionAndContent(msgs);

        // then
        Struct response = instructionAndContent
                .contents
                .get(0)
                .getParts(0)
                .getFunctionResponse()
                .getResponse();
        assertThat(response.getFieldsMap().get("name").getStringValue()).isEqualTo("John");
        assertThat(response.getFieldsMap().get("age").getNumberValue()).isEqualTo(30.0);
    }

    @Test
    void should_handle_tool_result_as_json_array() {
        // given
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(ToolExecutionResultMessage.from(null, "getList", "[1, 2, 3]"));

        // when
        ContentsMapper.InstructionAndContent instructionAndContent = splitInstructionAndContent(msgs);

        // then
        Struct response = instructionAndContent
                .contents
                .get(0)
                .getParts(0)
                .getFunctionResponse()
                .getResponse();
        assertThat(response.getFieldsMap()).containsKey("result");
        assertThat(response.getFieldsMap().get("result").getListValue().getValuesCount())
                .isEqualTo(3);
        assertThat(response.getFieldsMap()
                        .get("result")
                        .getListValue()
                        .getValues(0)
                        .getNumberValue())
                .isEqualTo(1.0);
        assertThat(response.getFieldsMap()
                        .get("result")
                        .getListValue()
                        .getValues(1)
                        .getNumberValue())
                .isEqualTo(2.0);
    }

    @Test
    void should_handle_tool_result_as_json_number() {
        // given
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(ToolExecutionResultMessage.from(null, "calculate", "42"));

        // when
        ContentsMapper.InstructionAndContent instructionAndContent = splitInstructionAndContent(msgs);

        // then
        Struct response = instructionAndContent
                .contents
                .get(0)
                .getParts(0)
                .getFunctionResponse()
                .getResponse();
        assertThat(response.getFieldsMap()).containsKey("result");
        assertThat(response.getFieldsMap().get("result").getNumberValue()).isEqualTo(42.0);
    }

    @Test
    void should_handle_tool_result_as_json_boolean() {
        // given
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(ToolExecutionResultMessage.from(null, "checkStatus", "true"));

        // when
        ContentsMapper.InstructionAndContent instructionAndContent = splitInstructionAndContent(msgs);

        // then
        Struct response = instructionAndContent
                .contents
                .get(0)
                .getParts(0)
                .getFunctionResponse()
                .getResponse();
        assertThat(response.getFieldsMap()).containsKey("result");
        assertThat(response.getFieldsMap().get("result").getBoolValue()).isTrue();
    }

    @Test
    void should_handle_tool_result_as_json_string() {
        // given
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(ToolExecutionResultMessage.from(null, "getMessage", "\"hello world\""));

        // when
        ContentsMapper.InstructionAndContent instructionAndContent = splitInstructionAndContent(msgs);

        // then
        Struct response = instructionAndContent
                .contents
                .get(0)
                .getParts(0)
                .getFunctionResponse()
                .getResponse();
        assertThat(response.getFieldsMap()).containsKey("result");
        assertThat(response.getFieldsMap().get("result").getStringValue()).isEqualTo("hello world");
    }

    @Test
    void should_handle_tool_result_as_plain_text() {
        // given
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(ToolExecutionResultMessage.from(null, "describe", "This is plain text output"));

        // when
        ContentsMapper.InstructionAndContent instructionAndContent = splitInstructionAndContent(msgs);

        // then
        Struct response = instructionAndContent
                .contents
                .get(0)
                .getParts(0)
                .getFunctionResponse()
                .getResponse();
        assertThat(response.getFieldsMap()).containsKey("result");
        assertThat(response.getFieldsMap().get("result").getStringValue()).isEqualTo("This is plain text output");
    }

    @Test
    void should_handle_tool_result_as_plain_text_with_embedded_quotes() {
        // given
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(ToolExecutionResultMessage.from(null, "add", "The result is \"79376135377\"."));

        // when
        ContentsMapper.InstructionAndContent instructionAndContent = splitInstructionAndContent(msgs);

        // then
        Struct response = instructionAndContent
                .contents
                .get(0)
                .getParts(0)
                .getFunctionResponse()
                .getResponse();
        assertThat(response.getFieldsMap()).containsKey("result");
        assertThat(response.getFieldsMap().get("result").getStringValue()).isEqualTo("The result is \"79376135377\".");
    }

    @Test
    void should_handle_tool_result_as_json_null() {
        // given
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(ToolExecutionResultMessage.from(null, "findItem", "null"));

        // when
        ContentsMapper.InstructionAndContent instructionAndContent = splitInstructionAndContent(msgs);

        // then
        Struct response = instructionAndContent
                .contents
                .get(0)
                .getParts(0)
                .getFunctionResponse()
                .getResponse();
        assertThat(response.getFieldsMap()).containsKey("result");
        assertThat(response.getFieldsMap().get("result").hasNullValue()).isTrue();
    }

    @Test
    void should_handle_tool_result_as_nested_json_object() {
        // given
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(ToolExecutionResultMessage.from(
                null, "getNestedData", "{\"user\": {\"name\": \"Alice\", \"address\": {\"city\": \"Paris\"}}}"));

        // when
        ContentsMapper.InstructionAndContent instructionAndContent = splitInstructionAndContent(msgs);

        // then
        Struct response = instructionAndContent
                .contents
                .get(0)
                .getParts(0)
                .getFunctionResponse()
                .getResponse();
        Struct user = response.getFieldsMap().get("user").getStructValue();
        assertThat(user.getFieldsMap().get("name").getStringValue()).isEqualTo("Alice");
        Struct address = user.getFieldsMap().get("address").getStructValue();
        assertThat(address.getFieldsMap().get("city").getStringValue()).isEqualTo("Paris");
    }

    @Test
    void should_handle_tool_result_as_negative_number() {
        // given
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(ToolExecutionResultMessage.from(null, "getDifference", "-42"));

        // when
        ContentsMapper.InstructionAndContent instructionAndContent = splitInstructionAndContent(msgs);

        // then
        Struct response = instructionAndContent
                .contents
                .get(0)
                .getParts(0)
                .getFunctionResponse()
                .getResponse();
        assertThat(response.getFieldsMap().get("result").getNumberValue()).isEqualTo(-42.0);
    }

    @Test
    void should_handle_tool_result_as_floating_point_number() {
        // given
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(ToolExecutionResultMessage.from(null, "getFloat", "5.64659"));

        // when
        ContentsMapper.InstructionAndContent instructionAndContent = splitInstructionAndContent(msgs);

        // then
        Struct response = instructionAndContent
                .contents
                .get(0)
                .getParts(0)
                .getFunctionResponse()
                .getResponse();
        assertThat(response.getFieldsMap().get("result").getNumberValue()).isEqualTo(5.64659);
    }

    @Test
    void should_handle_tool_result_as_empty_json_array() {
        // given
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(ToolExecutionResultMessage.from(null, "getEmptyList", "[]"));

        // when
        ContentsMapper.InstructionAndContent instructionAndContent = splitInstructionAndContent(msgs);

        // then
        Struct response = instructionAndContent
                .contents
                .get(0)
                .getParts(0)
                .getFunctionResponse()
                .getResponse();
        assertThat(response.getFieldsMap().get("result").getListValue().getValuesCount())
                .isZero();
    }

    @Test
    void should_handle_tool_result_as_array_of_objects() {
        // given
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(ToolExecutionResultMessage.from(null, "getUsers", "[{\"name\": \"Alice\"}, {\"name\": \"Bob\"}]"));

        // when
        ContentsMapper.InstructionAndContent instructionAndContent = splitInstructionAndContent(msgs);

        // then
        Struct response = instructionAndContent
                .contents
                .get(0)
                .getParts(0)
                .getFunctionResponse()
                .getResponse();
        assertThat(response.getFieldsMap().get("result").getListValue().getValuesCount())
                .isEqualTo(2);
        assertThat(response.getFieldsMap()
                        .get("result")
                        .getListValue()
                        .getValues(0)
                        .getStructValue()
                        .getFieldsMap()
                        .get("name")
                        .getStringValue())
                .isEqualTo("Alice");
    }

    @Test
    void should_handle_tool_result_with_special_characters() {
        // given
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(ToolExecutionResultMessage.from(null, "getSpecial", "{\"text\": \"Line1\\nLine2\\tTabbed\"}"));

        // when
        ContentsMapper.InstructionAndContent instructionAndContent = splitInstructionAndContent(msgs);

        // then
        Struct response = instructionAndContent
                .contents
                .get(0)
                .getParts(0)
                .getFunctionResponse()
                .getResponse();
        assertThat(response.getFieldsMap().get("text").getStringValue()).isEqualTo("Line1\nLine2\tTabbed");
    }

    @Test
    void should_handle_tool_result_with_unicode_characters() {
        // given
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(ToolExecutionResultMessage.from(null, "getEmoji", "{\"message\": \"Hello 👋 World 🌍\"}"));

        // when
        ContentsMapper.InstructionAndContent instructionAndContent = splitInstructionAndContent(msgs);

        // then
        Struct response = instructionAndContent
                .contents
                .get(0)
                .getParts(0)
                .getFunctionResponse()
                .getResponse();
        assertThat(response.getFieldsMap().get("message").getStringValue()).isEqualTo("Hello 👋 World 🌍");
    }

    @Test
    void should_handle_tool_result_as_scientific_notation_number() {
        // given
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(ToolExecutionResultMessage.from(null, "getScientific", "1.5e10"));

        // when
        ContentsMapper.InstructionAndContent instructionAndContent = splitInstructionAndContent(msgs);

        // then
        Struct response = instructionAndContent
                .contents
                .get(0)
                .getParts(0)
                .getFunctionResponse()
                .getResponse();
        assertThat(response.getFieldsMap().get("result").getNumberValue()).isEqualTo(1.5e10);
    }

    @Test
    void should_handle_tool_result_with_json_containing_backslashes() {
        // given
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(ToolExecutionResultMessage.from(null, "getPath", "{\"path\": \"C:\\\\Users\\\\test\"}"));

        // when
        ContentsMapper.InstructionAndContent instructionAndContent = splitInstructionAndContent(msgs);

        // then
        Struct response = instructionAndContent
                .contents
                .get(0)
                .getParts(0)
                .getFunctionResponse()
                .getResponse();
        assertThat(response.getFieldsMap().get("path").getStringValue()).isEqualTo("C:\\Users\\test");
    }

    @Test
    void should_handle_tool_result_as_mixed_type_array() {
        // given
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(ToolExecutionResultMessage.from(null, "getMixed", "[1, \"two\", true, null]"));

        // when
        ContentsMapper.InstructionAndContent instructionAndContent = splitInstructionAndContent(msgs);

        // then
        Struct response = instructionAndContent
                .contents
                .get(0)
                .getParts(0)
                .getFunctionResponse()
                .getResponse();
        assertThat(response.getFieldsMap().get("result").getListValue().getValuesCount())
                .isEqualTo(4);
        assertThat(response.getFieldsMap()
                        .get("result")
                        .getListValue()
                        .getValues(0)
                        .getNumberValue())
                .isEqualTo(1.0);
        assertThat(response.getFieldsMap()
                        .get("result")
                        .getListValue()
                        .getValues(1)
                        .getStringValue())
                .isEqualTo("two");
        assertThat(response.getFieldsMap()
                        .get("result")
                        .getListValue()
                        .getValues(2)
                        .getBoolValue())
                .isTrue();
        assertThat(response.getFieldsMap()
                        .get("result")
                        .getListValue()
                        .getValues(3)
                        .hasNullValue())
                .isTrue();
    }

    @Test
    void should_handle_tool_result_as_empty_json_object() {
        // given
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(ToolExecutionResultMessage.from(null, "getEmptyObject", "{}"));

        // when
        ContentsMapper.InstructionAndContent instructionAndContent = splitInstructionAndContent(msgs);

        // then
        Struct response = instructionAndContent
                .contents
                .get(0)
                .getParts(0)
                .getFunctionResponse()
                .getResponse();
        assertThat(response.getFieldsCount()).isZero();
    }

    @Test
    void should_handle_tool_result_text_starting_with_curly_brace_but_invalid_json() {
        // given
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(ToolExecutionResultMessage.from(null, "getInvalid", "{not valid json}"));

        // when
        ContentsMapper.InstructionAndContent instructionAndContent = splitInstructionAndContent(msgs);

        // then
        Struct response = instructionAndContent
                .contents
                .get(0)
                .getParts(0)
                .getFunctionResponse()
                .getResponse();
        assertThat(response.getFieldsMap()).containsKey("result");
        assertThat(response.getFieldsMap().get("result").getStringValue()).isEqualTo("{not valid json}");
    }

    @Test
    void should_handle_tool_result_text_starting_with_bracket_but_invalid_json() {
        // given
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(ToolExecutionResultMessage.from(null, "getInvalidArray", "[not, valid, json"));

        // when
        ContentsMapper.InstructionAndContent instructionAndContent = splitInstructionAndContent(msgs);

        // then
        Struct response = instructionAndContent
                .contents
                .get(0)
                .getParts(0)
                .getFunctionResponse()
                .getResponse();
        assertThat(response.getFieldsMap()).containsKey("result");
        assertThat(response.getFieldsMap().get("result").getStringValue()).isEqualTo("[not, valid, json");
    }

    @Test
    void should_handle_tool_result_with_html_content() {
        // given
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(ToolExecutionResultMessage.from(null, "getHtml", "<html><body>Hello</body></html>"));

        // when
        ContentsMapper.InstructionAndContent instructionAndContent = splitInstructionAndContent(msgs);

        // then
        Struct response = instructionAndContent
                .contents
                .get(0)
                .getParts(0)
                .getFunctionResponse()
                .getResponse();
        assertThat(response.getFieldsMap()).containsKey("result");
        assertThat(response.getFieldsMap().get("result").getStringValue()).isEqualTo("<html><body>Hello</body></html>");
    }

    @Test
    void should_handle_tool_result_with_xml_content() {
        // given
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(ToolExecutionResultMessage.from(
                null, "getXml", "<?xml version=\"1.0\"?><root><item>value</item></root>"));

        // when
        ContentsMapper.InstructionAndContent instructionAndContent = splitInstructionAndContent(msgs);

        // then
        Struct response = instructionAndContent
                .contents
                .get(0)
                .getParts(0)
                .getFunctionResponse()
                .getResponse();
        assertThat(response.getFieldsMap()).containsKey("result");
        assertThat(response.getFieldsMap().get("result").getStringValue())
                .isEqualTo("<?xml version=\"1.0\"?><root><item>value</item></root>");
    }

    @Test
    void should_handle_tool_result_with_json_containing_url() {
        // given
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(ToolExecutionResultMessage.from(
                null, "getUrl", "{\"url\": \"https://example.com/path?query=value&other=123\"}"));

        // when
        ContentsMapper.InstructionAndContent instructionAndContent = splitInstructionAndContent(msgs);

        // then
        Struct response = instructionAndContent
                .contents
                .get(0)
                .getParts(0)
                .getFunctionResponse()
                .getResponse();
        assertThat(response.getFieldsMap().get("url").getStringValue())
                .isEqualTo("https://example.com/path?query=value&other=123");
    }
}
