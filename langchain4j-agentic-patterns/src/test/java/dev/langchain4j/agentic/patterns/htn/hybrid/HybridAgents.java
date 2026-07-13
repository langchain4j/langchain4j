package dev.langchain4j.agentic.patterns.htn.hybrid;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.observability.MonitoredAgent;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public class HybridAgents {

    public interface AnalyzeRequirements {
        @UserMessage("""
                Analyze the following system specification. Extract the key functional requirements, \
                non-functional requirements, and determine the project complexity (HIGH, MEDIUM, or LOW). \
                Return a structured analysis in 4-6 bullet points.
                Specification: {{systemSpec}}
                """)
        @Agent("Analyze system requirements and determine complexity")
        String analyze(@V("systemSpec") String systemSpec);
    }

    public interface ArchitectureReview {
        @UserMessage("""
                Review the following requirements and propose a high-level system architecture. \
                Include component diagram description, technology choices, and integration patterns. \
                Return a concise architecture overview.
                Requirements: {{requirements}}
                """)
        @Agent("Review architecture for complex systems")
        String review(@V("requirements") String requirements);
    }

    public interface DesignDatabase {
        @UserMessage("""
                Design the database schema for the system based on the requirements. \
                Include entity descriptions, relationships, and key indexes. \
                Return a concise schema design.
                Requirements: {{requirements}}
                """)
        @Agent("Design the database schema")
        String design(@V("requirements") String requirements);
    }

    public interface DesignAPI {
        @UserMessage("""
                Design the REST API for the system based on the requirements. \
                Include endpoint definitions, request/response formats, and authentication approach. \
                Return a concise API design.
                Requirements: {{requirements}}
                """)
        @Agent("Design the REST API endpoints")
        String design(@V("requirements") String requirements);
    }

    public interface DesignUI {
        @UserMessage("""
                Design the user interface for the system based on the requirements. \
                Include key screens, navigation flow, and component hierarchy. \
                Return a concise UI design.
                Requirements: {{requirements}}
                """)
        @Agent("Design the user interface")
        String design(@V("requirements") String requirements);
    }

    public interface QuickDesign {
        @UserMessage("""
                Create a quick, simplified design for the system based on the requirements. \
                Cover database, API, and UI in a single concise document.
                Requirements: {{requirements}}
                """)
        @Agent("Create a quick simplified design for simple systems")
        String design(@V("requirements") String requirements);
    }

    public interface CreateTimeline {
        @UserMessage("""
                Create a project timeline based on the requirements and any design artifacts available. \
                Break down into phases with estimated durations. Return a concise timeline.
                Requirements: {{requirements}}
                """)
        @Agent("Create a project timeline with milestones")
        String create(@V("requirements") String requirements);
    }

    public interface SystemDesigner extends MonitoredAgent {
        @Agent
        ResultWithAgenticScope<String> design(@V("systemSpec") String spec);
    }
}
