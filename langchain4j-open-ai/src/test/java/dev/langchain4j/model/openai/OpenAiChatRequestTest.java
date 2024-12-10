//package dev.langchain4j.model.openai;
//
//import dev.langchain4j.data.message.UserMessage;
//import org.junit.jupiter.api.Test;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//class OpenAiChatRequestTest { TODO
//
//    private static final UserMessage USER_MESSAGE = UserMessage.from("Hi");
//    private static final int SEED = 123;
//    private static final double TEMPERATURE = 1.0;
//
//    @Test
//    void should_set_common_parameters_then_OpenAI_specific_parameters() {
//
//        // when
//        OpenAiChatRequest chatRequest = OpenAiChatRequest.builder()
//                .messages(USER_MESSAGE) // first set common parameters
//                .temperature(TEMPERATURE) // first set common parameters
//                .seed(SEED) // then set OpenAI-specific parameters
//                .build();
//
//        // then
//        assertThat(chatRequest.messages()).containsExactly(USER_MESSAGE);
//        assertThat(chatRequest.temperature()).isEqualTo(TEMPERATURE);
//        assertThat(chatRequest.seed()).isEqualTo(SEED);
//    }
//
//    @Test
//    void should_set_OpenAI_specific_parameters_then_common_parameters() {
//
//        // when
//        OpenAiChatRequest chatRequest = OpenAiChatRequest.builder()
//                .seed(SEED) // first set OpenAI-specific parameters
//                .messages(USER_MESSAGE) // then set common parameters
//                .temperature(TEMPERATURE) // then set common parameters
//                .build();
//
//        // then
//        assertThat(chatRequest.seed()).isEqualTo(SEED);
//        assertThat(chatRequest.messages()).containsExactly(USER_MESSAGE);
//        assertThat(chatRequest.temperature()).isEqualTo(TEMPERATURE);
//    }
//}
