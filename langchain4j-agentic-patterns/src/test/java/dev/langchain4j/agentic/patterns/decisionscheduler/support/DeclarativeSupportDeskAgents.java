package dev.langchain4j.agentic.patterns.decisionscheduler.support;

import static dev.langchain4j.agentic.patterns.Models.baseModel;

import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.PlannerAgent;
import dev.langchain4j.agentic.declarative.PlannerSupplier;
import dev.langchain4j.agentic.patterns.decisionscheduler.support.SupportDeskAgents.OrderTracker;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.V;

public class DeclarativeSupportDeskAgents {

    public interface DeclarativeRefundPolicyAdvisor extends SupportDeskAgents.RefundPolicyAdvisor {

        @ChatModelSupplier
        static ChatModel chatModel() {
            return baseModel();
        }
    }

    public interface DeclarativeTroubleshooter extends SupportDeskAgents.Troubleshooter {

        @ChatModelSupplier
        static ChatModel chatModel() {
            return baseModel();
        }
    }

    public interface DeclarativeReplyWriter extends SupportDeskAgents.ReplyWriter {

        @ChatModelSupplier
        static ChatModel chatModel() {
            return baseModel();
        }
    }

    public interface DeclarativeSupportDesk {

        @PlannerAgent(
                outputKey = "reply",
                subAgents = {
                    OrderTracker.class,
                    DeclarativeRefundPolicyAdvisor.class,
                    DeclarativeTroubleshooter.class,
                    DeclarativeReplyWriter.class
                })
        ResultWithAgenticScope<String> handle(@V("ticket") String ticket);

        @PlannerSupplier
        static Planner planner() {
            return SupportDeskAgents.planner();
        }
    }
}
