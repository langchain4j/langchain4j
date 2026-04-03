package dev.langchain4j.agentic;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.StreamingChatModelSupplier;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import static dev.langchain4j.agentic.Models.streamingBaseModel;

public class StreamingAgents {

    public interface StreamingCreativeWriter {

        @UserMessage("""
                You are a creative writer.
                Generate a draft of a story long no more than 3 sentence around the given topic.
                Return only the story and nothing else.
                The topic is {{topic}}.
                """)
        @Agent(description = "Generate a story based on the given topic", outputKey = "story")
        TokenStream generateStory(@V("topic") String topic);
    }

    public interface StreamingAudienceEditor {

        @UserMessage("""
            You are a professional editor.
            Analyze and rewrite the following story to better align with the target audience of {{audience}}.
            Return only the story and nothing else.
            The story is "{{story}}".
            """)
        @Agent(description = "Edit a story to better fit a given audience", outputKey = "story")
        TokenStream editStory(@V("story") String story, @V("audience") String audience);
    }

    public interface StreamingStyleEditor {

        @UserMessage("""
                You are a professional editor.
                Analyze and rewrite the following story to better fit and be more coherent with the {{style}} style.
                Return only the story and nothing else.
                The story is "{{story}}".
                """)
        @Agent(description = "Edit a story to better fit a given style", outputKey = "story")
        TokenStream editStory(@V("story") String story, @V("style") String style);
    }

    public interface StreamingReviewedWriter {
        @Agent
        TokenStream writeStory(@V("topic") String topic, @V("audience") String audience, @V("style") String style);
    }

    public interface StreamingExpertRouterAgent {

        @Agent
        TokenStream ask(@V("request") String request);
    }

    public interface StreamingMedicalExpert {

        @UserMessage("""
            You are a medical expert.
            Analyze the following user request under a medical point of view and provide the best possible answer.
            The user request is {{request}}.
            """)
        @Tool("A medical expert")
        @Agent(description = "A medical expert", outputKey = "response")
        TokenStream medical(@V("request") String request);
    }

    public interface StreamingLegalExpert {

        @UserMessage("""
            You are a legal expert.
            Analyze the following user request under a legal point of view and provide the best possible answer.
            The user request is {{request}}.
            """)
        @Agent(description = "A legal expert", outputKey = "response")
        TokenStream legal(@V("request") String request);
    }

    public interface StreamingTechnicalExpert {

        @UserMessage("""
            You are a technical expert.
            Analyze the following user request under a technical point of view and provide the best possible answer.
            The user request is {{request}}.
            """)
        @Agent("A technical expert")
        TokenStream technical(@V("request") String request);
    }

    public interface StreamingCreativeWriterWithModel extends StreamingCreativeWriter {
        @StreamingChatModelSupplier
        static StreamingChatModel chatModel() {
            return streamingBaseModel();
        }
    }

    public interface StreamingAudienceEditorWithModel extends StreamingAudienceEditor {
        @StreamingChatModelSupplier
        static StreamingChatModel chatModel() {
            return streamingBaseModel();
        }
    }

    public interface StreamingStyleEditorWithModel extends StreamingStyleEditor {
        @StreamingChatModelSupplier
        static StreamingChatModel chatModel() {
            return streamingBaseModel();
        }
    }

    public interface StreamingStoryCreator {

        @SequenceAgent( outputKey = "story",
                subAgents = { StreamingCreativeWriterWithModel.class, StreamingAudienceEditorWithModel.class, StreamingStyleEditorWithModel.class})
        TokenStream write(@V("topic") String topic, @V("style") String style, @V("audience") String audience);
    }
}
