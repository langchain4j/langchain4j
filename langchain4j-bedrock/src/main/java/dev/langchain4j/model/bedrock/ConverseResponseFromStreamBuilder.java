package dev.langchain4j.model.bedrock;

import static dev.langchain4j.model.bedrock.AwsDocumentConverter.documentFromJson;
import static java.util.Objects.nonNull;

import java.util.ArrayList;
import java.util.List;

import dev.langchain4j.Internal;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDelta;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDeltaEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStart;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStartEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStopEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamMetadataEvent;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.MessageStartEvent;
import software.amazon.awssdk.services.bedrockruntime.model.MessageStopEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

@Internal
class ConverseResponseFromStreamBuilder {

    private final ConverseResponse.Builder converseResponseBuilder = ConverseResponse.builder();
    private Message.Builder messageBuilder = Message.builder();
    private final StringBuilder stringBuilder = new StringBuilder();
    private ToolUseBlock.Builder toolUseBlockBuilder = null;
    private StringBuilder toolUseInputBuilder = new StringBuilder();
    private final List<ToolUseBlock> toolUseBlocks = new ArrayList<>();

    public static ConverseResponseFromStreamBuilder builder() {
        return new ConverseResponseFromStreamBuilder();
    }

    public ConverseResponseFromStreamBuilder append(ContentBlockStartEvent contentBlockStartEvent) {
        if (contentBlockStartEvent.start().type().equals(ContentBlockStart.Type.TOOL_USE)) {
            toolUseBlockBuilder = ToolUseBlock.builder()
                    .toolUseId(contentBlockStartEvent.start().toolUse().toolUseId())
                    .name(contentBlockStartEvent.start().toolUse().name());
        }

        return this;
    }

    public ConverseResponseFromStreamBuilder append(ContentBlockDeltaEvent contentBlockDeltaEvent) {
        if (contentBlockDeltaEvent.delta().type().equals(ContentBlockDelta.Type.TEXT)) {
            stringBuilder.append(contentBlockDeltaEvent.delta().text());
        } else if (contentBlockDeltaEvent.delta().type().equals(ContentBlockDelta.Type.TOOL_USE)) {
            toolUseInputBuilder.append(contentBlockDeltaEvent.delta().toolUse().input());
        }
        return this;
    }

    public ConverseResponseFromStreamBuilder append(ContentBlockStopEvent contentBlockStopEvent) {
        if (nonNull(this.toolUseBlockBuilder)) {
            if (!toolUseInputBuilder.isEmpty())
                toolUseBlockBuilder.input(documentFromJson(toolUseInputBuilder.toString()));
            this.toolUseBlocks.add(this.toolUseBlockBuilder.build());

            this.toolUseInputBuilder = new StringBuilder();
            this.toolUseBlockBuilder = null;
        }
        return this;
    }

    public ConverseResponseFromStreamBuilder append(ConverseStreamMetadataEvent metadataEvent) {
        converseResponseBuilder.usage(metadataEvent.usage());
        converseResponseBuilder.metrics(builder ->
                builder.latencyMs(metadataEvent.metrics().latencyMs()).build());
        return this;
    }

    public ConverseResponseFromStreamBuilder append(MessageStartEvent messageStartEvent) {
        messageBuilder = Message.builder();
        messageBuilder.role(messageStartEvent.role());
        return this;
    }

    public ConverseResponseFromStreamBuilder append(MessageStopEvent messageStopEvent) {
        converseResponseBuilder.stopReason(messageStopEvent.stopReason());
        converseResponseBuilder.additionalModelResponseFields(messageStopEvent.additionalModelResponseFields());
        if (nonNull(this.messageBuilder)) {
            ArrayList<ContentBlock> contents = new ArrayList<>();
            contents.add(ContentBlock.builder().text(stringBuilder.toString()).build());
            contents.addAll(
                    this.toolUseBlocks.stream().map(ContentBlock::fromToolUse).toList());
            converseResponseBuilder.output(builder -> builder.message(
                            this.messageBuilder.content(contents).build())
                    .build());
            this.messageBuilder = null;
        }
        return this;
    }

    public ConverseResponse build() {
        return converseResponseBuilder.build();
    }
}
