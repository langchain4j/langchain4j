package dev.langchain4j.agentic.patterns.htn.launch;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.observability.MonitoredAgent;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public class LaunchAgents {

    public interface AnalyzeProduct {
        @UserMessage("""
                Analyze the following product for a go-to-market launch. Identify the product category, \
                key differentiators, competitive landscape, and target market segments. \
                Return a structured analysis in 4-6 bullet points.
                Product: {{product}}
                """)
        @Agent("Analyze the product and its market positioning")
        String analyze(@V("product") String product);
    }

    public interface DefineAudience {
        @UserMessage("""
                Based on the following product analysis, define 2-3 target audience segments. \
                For each segment include demographics, pain points, and preferred channels.
                Product analysis: {{productAnalysis}}
                """)
        @Agent("Define target audience segments for the product")
        String defineAudience(@V("productAnalysis") String productAnalysis);
    }

    public interface CraftMessaging {
        @UserMessage("""
                Create marketing messaging for the product based on the target audience. \
                Include a tagline, value proposition, and 3 key selling points.
                Product analysis: {{productAnalysis}}
                Audience: {{audience}}
                """)
        @Agent("Craft key marketing messages and value proposition")
        String craftMessaging(@V("productAnalysis") String productAnalysis, @V("audience") String audience);
    }

    public interface PlanDigitalMarketing {
        @UserMessage("""
                Plan a digital marketing campaign for the product launch. \
                Cover social media strategy, paid advertising, and email marketing. Return a concise plan.
                Product analysis: {{productAnalysis}}
                Messaging: {{messaging}}
                """)
        @Agent("Plan digital marketing campaigns including social media and ads")
        String planDigitalMarketing(@V("productAnalysis") String productAnalysis, @V("messaging") String messaging);
    }

    public interface CreatePRStrategy {
        @UserMessage("""
                Create a PR strategy for the product launch. Include press release outline, \
                media outreach targets, and key talking points. Return a concise plan.
                Product analysis: {{productAnalysis}}
                Messaging: {{messaging}}
                """)
        @Agent("Create public relations strategy and press release plan")
        String createPRStrategy(@V("productAnalysis") String productAnalysis, @V("messaging") String messaging);
    }

    public interface PlanLaunchEvent {
        @UserMessage("""
                Plan a launch event or webinar for the product. Include format, \
                agenda highlights, and audience engagement strategy. Return a concise plan.
                Product analysis: {{productAnalysis}}
                """)
        @Agent("Plan the product launch event or webinar")
        String planLaunchEvent(@V("productAnalysis") String productAnalysis);
    }

    public interface CompileLaunchPlan {
        @UserMessage("""
                Compile a final product launch plan from all gathered information into a professional summary. \
                Include all available sections.
                Product analysis: {{productAnalysis}}
                """)
        @Agent("Compile the final product launch plan")
        String compilePlan(@V("productAnalysis") String productAnalysis);
    }

    public interface ProductLauncher extends MonitoredAgent {
        @Agent
        ResultWithAgenticScope<String> launch(@V("product") String product);
    }
}
