package dev.langchain4j.model.vertexai.gemini;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.vertexai.api.Candidate;
import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.api.FunctionCall;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.Part;
import com.google.protobuf.ByteString;
import com.google.protobuf.Struct;
import com.google.protobuf.UnknownFieldSet;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

class StreamingChatResponseBuilderTest {

    @Test
    void should_preserve_function_call_part_thought_signature_when_return_thinking_is_enabled() {
        // given
        Part functionCallPart = Part.newBuilder()
                .setFunctionCall(FunctionCall.newBuilder().setName("getWeather").setArgs(Struct.newBuilder()))
                .mergeUnknownFields(unknownFields("thought-signature"))
                .build();
        GenerateContentResponse partialResponse = GenerateContentResponse.newBuilder()
                .addCandidates(
                        Candidate.newBuilder().setContent(Content.newBuilder().addParts(functionCallPart)))
                .build();

        StreamingChatResponseBuilder builder = new StreamingChatResponseBuilder(true);

        // when
        builder.append(partialResponse);
        Response<AiMessage> response = builder.build();
        Part sameFunctionCallPart =
                FunctionCallHelper.toFunctionCallParts(response.content(), true).get(0);

        // then
        assertThat(sameFunctionCallPart.getFunctionCall()).isEqualTo(functionCallPart.getFunctionCall());
        assertThat(sameFunctionCallPart.getUnknownFields()).isEqualTo(functionCallPart.getUnknownFields());
    }

    private static UnknownFieldSet unknownFields(String thoughtSignature) {
        return UnknownFieldSet.newBuilder()
                .addField(
                        11,
                        UnknownFieldSet.Field.newBuilder()
                                .addLengthDelimited(ByteString.copyFromUtf8(thoughtSignature))
                                .build())
                .build();
    }
}
