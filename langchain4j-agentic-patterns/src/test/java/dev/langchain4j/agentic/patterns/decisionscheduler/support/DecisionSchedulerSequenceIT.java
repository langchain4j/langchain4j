package dev.langchain4j.agentic.patterns.decisionscheduler.support;

import static dev.langchain4j.agentic.patterns.Models.baseModel;
import static dev.langchain4j.agentic.patterns.decisionscheduler.support.SupportDeskAgents.PARCEL_TICKET;
import static dev.langchain4j.agentic.patterns.decisionscheduler.support.SupportDeskAgents.invokedSpecialists;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.patterns.decisionscheduler.support.SupportDeskAgents.ReplySummarizer;
import dev.langchain4j.agentic.patterns.decisionscheduler.support.SupportDeskAgents.SupportPipeline;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TYPESAFE_API_KEY", matches = ".+")
public class DecisionSchedulerSequenceIT {

    @Test
    void sequence_of_support_desk_and_summarizer() {
        ReplySummarizer summarizer = AgenticServices.agentBuilder(ReplySummarizer.class)
                .chatModel(baseModel())
                .build();

        SupportPipeline pipeline = AgenticServices.sequenceBuilder(SupportPipeline.class)
                .subAgents(DecisionSchedulerSupportDeskIT.supportDesk(), summarizer)
                .outputKey("summary")
                .build();

        ResultWithAgenticScope<String> result = pipeline.process(PARCEL_TICKET);

        assertThat(result.result()).isNotBlank();
        assertThat(invokedSpecialists(result.agenticScope()))
                .startsWith("orderTracker")
                .endsWith("replyWriter");
        assertThat(result.agenticScope().readState("reply", "")).containsIgnoringCase("customs");
        assertThat(result.agenticScope().agentInvocations("replySummarizer")).hasSize(1);
    }
}
