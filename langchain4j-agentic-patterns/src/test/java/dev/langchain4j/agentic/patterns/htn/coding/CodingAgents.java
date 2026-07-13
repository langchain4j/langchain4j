package dev.langchain4j.agentic.patterns.htn.coding;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.observability.MonitoredAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public class CodingAgents {

    public interface AnalyzeSpecs {
        @UserMessage("""
                Analyze the following software specification. Extract the key features, \
                technical requirements, and determine the project complexity (HIGH, MEDIUM, or LOW). \
                Also identify the recommended tech stack. Return a structured analysis.
                Specification: {{specs}}
                """)
        @Agent("Analyze software specifications and determine complexity")
        String analyze(@V("specs") String specs);
    }

    public interface DesignArchitecture {
        @UserMessage("""
                Design the software architecture based on the specification analysis. \
                Include component structure, design patterns, and layer organization. \
                Return a concise architecture document.
                Analysis: {{specAnalysis}}
                """)
        @Agent("Design the software architecture")
        String design(@V("specAnalysis") String specAnalysis);
    }

    public interface DefineAPIs {
        @UserMessage("""
                Define the API contracts based on the architecture. \
                Include endpoint signatures, data transfer objects, and error contracts. \
                Return concise API definitions.
                Architecture: {{architecture}}
                """)
        @Agent("Define API contracts and interfaces")
        String define(@V("architecture") String architecture);
    }

    public interface DesignDataModel {
        @UserMessage("""
                Design the data model based on the architecture. \
                Include entity definitions, relationships, and validation rules. \
                Return a concise data model.
                Architecture: {{architecture}}
                """)
        @Agent("Design the data model and entities")
        String design(@V("architecture") String architecture);
    }

    public interface ImplementDataLayer {
        @UserMessage("""
                Generate the data layer implementation code based on the architecture and data model. \
                Include repository interfaces, entity classes, and database configuration. \
                Return the code.
                Architecture: {{architecture}}
                Data model: {{dataModel}}
                """)
        @Agent("Implement the data access layer")
        String implement(@V("architecture") String architecture, @V("dataModel") String dataModel);
    }

    public interface ImplementServiceLayer {
        @UserMessage("""
                Generate the service layer implementation code based on the architecture and API contracts. \
                Include service classes, business logic, and validation. \
                Return the code.
                Architecture: {{architecture}}
                API contracts: {{apiContracts}}
                """)
        @Agent("Implement the service/business logic layer")
        String implement(@V("architecture") String architecture, @V("apiContracts") String apiContracts);
    }

    public interface ImplementController {
        @UserMessage("""
                Generate the controller layer implementation code based on the architecture and API contracts. \
                Include REST controllers, request/response mapping, and error handling. \
                Return the code.
                Architecture: {{architecture}}
                API contracts: {{apiContracts}}
                """)
        @Agent("Implement the controller/API layer")
        String implement(@V("architecture") String architecture, @V("apiContracts") String apiContracts);
    }

    public interface ImplementUI {
        @UserMessage("""
                Generate the UI implementation code based on the architecture. \
                Include component structure, state management, and routing. \
                Return the code.
                Architecture: {{architecture}}
                """)
        @Agent("Implement the user interface layer")
        String implement(@V("architecture") String architecture);
    }

    public interface WriteTests {
        @UserMessage("""
                Generate test code for the implemented system. \
                Include unit tests, integration tests, and test data setup. \
                Return the test code.
                Architecture: {{architecture}}
                """)
        @Agent("Write test suites for the implementation")
        String write(@V("architecture") String architecture);
    }

    public static class RunTests {
        @Agent(name = "runTests", value = "Run the test suite and report results")
        public void run(AgenticScope scope) {
            scope.writeState("testResult", "PASS");
        }
    }

    public interface ReviewCode {
        @UserMessage("""
                Review the code for quality, security, and best practices. \
                Check for common issues and suggest improvements. \
                Return a concise review summary.
                Architecture: {{architecture}}
                Test results: {{testResult}}
                """)
        @Agent("Review code quality and suggest improvements")
        String review(@V("architecture") String architecture, @V("testResult") String testResult);
    }

    public interface FixIssues {
        @UserMessage("""
                Fix the issues identified in the code review. \
                Apply corrections and return the fixed code.
                Review: {{reviewResult}}
                """)
        @Agent("Fix issues identified during code review")
        String fix(@V("reviewResult") String reviewResult);
    }

    public interface GenerateDocumentation {
        @UserMessage("""
                Generate documentation for the implemented system. \
                Include API documentation, setup guide, and architecture overview. \
                Return concise documentation.
                Architecture: {{architecture}}
                API contracts: {{apiContracts}}
                """)
        @Agent("Generate project documentation")
        String generate(@V("architecture") String architecture, @V("apiContracts") String apiContracts);
    }

    public interface CodingAgent extends MonitoredAgent {
        @Agent
        ResultWithAgenticScope<String> generate(@V("specs") String specs);
    }
}
