package dev.langchain4j.agentic.supervisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.service.V;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SupervisorPlannerTest {

    record RecordFilter(String name, List<Integer> values) {}

    interface Arguments {

        void search(
                @V("fields") List<String> fields,
                @V("recordIds") String[] recordIds,
                @V("filters") Map<String, Object> filters,
                @V("unknown") Object unknown,
                @V("recordFilter") RecordFilter recordFilter);
    }

    @Test
    void builds_actionable_agent_card_for_generic_arrays_maps_and_objects() throws Exception {
        Method method = Arguments.class.getMethod(
                "search", List.class, String[].class, Map.class, Object.class, RecordFilter.class);

        AgentInstance agent = mock(AgentInstance.class);
        when(agent.agentId()).thenReturn("search");
        when(agent.description()).thenReturn("Search records");
        when(agent.arguments())
                .thenReturn(List.of(
                        new AgentArgument(
                                method.getGenericParameterTypes()[0],
                                "fields",
                                null,
                                false,
                                "Fields to include in the search"),
                        new AgentArgument(method.getGenericParameterTypes()[1], "recordIds"),
                        new AgentArgument(method.getGenericParameterTypes()[2], "filters"),
                        new AgentArgument(method.getGenericParameterTypes()[3], "unknown"),
                        new AgentArgument(method.getGenericParameterTypes()[4], "recordFilter")));

        Method cardBuilder = SupervisorPlanner.class.getDeclaredMethod("toCard", AgentInstance.class);
        cardBuilder.setAccessible(true);

        assertThat(cardBuilder.invoke(null, agent))
                .isEqualTo(
                        "{'search', 'Search records', [fields: List<String> - Fields to include in the search, recordIds: String[], "
                                + "filters: Map<String, Object>, unknown: Object, "
                                + "recordFilter: {name: String, values: List<Integer>}]}");
    }
}
