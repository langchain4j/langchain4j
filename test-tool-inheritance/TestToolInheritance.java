import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import java.util.List;

// Base class with @Tool method
class BaseTool {
    @Tool("Base tool method")
    public String baseTool() {
        return "base";
    }
}

// Child class that should inherit @Tool method
class ChildTool extends BaseTool {
    @Tool("Child tool method")
    public String childTool() {
        return "child";
    }
}

public class TestToolInheritance {
    public static void main(String[] args) {
        List<ToolSpecification> specs = ToolSpecifications.toolSpecificationsFrom(ChildTool.class);
        System.out.println("Found " + specs.size() + " tool(s):");
        for (ToolSpecification spec : specs) {
            System.out.println("  - " + spec.name() + ": " + spec.description());
        }

        // Expected: 2 tools (baseTool + childTool)
        // Actual bug: only 1 tool (childTool)
        if (specs.size() == 2) {
            System.out.println("\n✓ PASS: Found both parent and child tools");
        } else {
            System.out.println("\n✗ FAIL: Expected 2 tools, got " + specs.size());
        }
    }
}
