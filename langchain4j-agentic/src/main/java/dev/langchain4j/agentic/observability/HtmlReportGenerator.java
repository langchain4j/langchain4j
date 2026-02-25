package dev.langchain4j.agentic.observability;

import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.workflow.ConditionalAgent;
import dev.langchain4j.agentic.workflow.ConditionalAgentInstance;
import dev.langchain4j.agentic.workflow.LoopAgentInstance;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates HTML reports for agent executions.
 * This class has been vibe-coded and is not expected to be maintainable manually by a human without LLM's help.
 */
public record HtmlReportGenerator(AgentMonitor monitor, AgentInstance rootAgent, Object memoryId) {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    public static void generateTopology(Object rootAgent, Path path) {
        try {
            Files.writeString(path, generateTopology(rootAgent));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String generateTopology(Object rootAgent) {
        return new HtmlReportGenerator(null, (AgentInstance) rootAgent, null).generateReport();
    }

    public static void generateReport(AgentMonitor monitor, Path path) {
        try {
            Files.writeString(path, generateReport(monitor));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String generateReport(AgentMonitor monitor) {
        return new HtmlReportGenerator(monitor, monitor.rootAgent(), null).generateReport();
    }

    public static void generateReport(AgentMonitor monitor, Object memoryId, Path path) {
        try {
            Files.writeString(path, generateReport(monitor, memoryId));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String generateReport(AgentMonitor monitor, Object memoryId) {
        return new HtmlReportGenerator(monitor, monitor.rootAgent(), memoryId).generateReport();
    }

    private String generateReport() {
        StringBuilder html = new StringBuilder(16384);
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n");
        appendHead(html);
        html.append("<body>\n");
        appendNavbar(html);
        html.append("<main class=\"container\">\n");
        appendTopologySection(html);
        if (monitor != null) {
            appendExecutionsSection(html);
        }
        appendFooter(html);
        html.append("</main>\n");
        appendScript(html);
        html.append("</body>\n</html>");
        return html.toString();
    }

    // -----------------------------------------------------------------------
    // Head
    // -----------------------------------------------------------------------

    private String name() {
        return monitor != null ? "LangChain4j Agentic System Report" : "LangChain4j Agentic System Topology";
    }

    private void appendHead(StringBuilder html) {
        html.append("<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("<title>").append(esc(name())).append("</title>\n");
        appendStyles(html);
        html.append("</head>\n");
    }

    private void appendStyles(StringBuilder html) {
        html.append("<style>\n");
        html.append(CSS);
        html.append("</style>\n");
    }

    // -----------------------------------------------------------------------
    // Navbar
    // -----------------------------------------------------------------------

    private void appendNavbar(StringBuilder html) {
        html.append("<nav class=\"navbar\">\n");
        html.append("  <div class=\"navbar-logo\">").append(LOGO_SVG).append("</div>\n");
        html.append("  <div class=\"navbar-title\">").append(esc(name())).append("</div>\n");
        html.append("  <div class=\"navbar-subtitle\">LangChain4j Agentic System</div>\n");
        html.append("</nav>\n");
    }

    // -----------------------------------------------------------------------
    // Topology
    // -----------------------------------------------------------------------

    private void appendTopologySection(StringBuilder html) {
        Map<String, String> keyColors = collectStateKeyColors(rootAgent);

        html.append("<section class=\"section\">\n");
        html.append("<div class=\"section-header\">\n");
        html.append("  <div class=\"section-icon\">").append(ICON_TOPOLOGY).append("</div>\n");
        html.append("  <h2 class=\"section-title\">System Topology</h2>\n");
        html.append("</div>\n");

        appendLegend(html);

        if (!keyColors.isEmpty()) {
            appendDataFlowLegend(html, keyColors);
        }

        html.append("<div class=\"topology-scroll\">\n");
        html.append("<div class=\"org-tree\" style=\"position:relative\">\n");
        html.append("<svg id=\"df-svg\" class=\"df-svg\"><defs>");
        for (Map.Entry<String, String> kc : keyColors.entrySet()) {
            String id = "ah-" + Math.abs(kc.getKey().hashCode());
            html.append("<marker id=\"").append(id).append("\" viewBox=\"0 0 10 10\" refX=\"10\" refY=\"5\"");
            html.append(" markerWidth=\"5\" markerHeight=\"5\" orient=\"auto\">");
            html.append("<path d=\"M0 0 L10 5 L0 10 z\" fill=\"")
                    .append(kc.getValue())
                    .append("\"/>");
            html.append("</marker>");
        }
        html.append("</defs></svg>\n");
        html.append("<ul>\n");
        appendTopologyNode(html, rootAgent, null, keyColors);
        html.append("</ul></div>\n");
        html.append("</div>\n");

        html.append("</section>\n");
    }

    private void appendDataFlowLegend(StringBuilder html, Map<String, String> keyColors) {
        html.append("<div class=\"legend df-legend\">\n");
        html.append("<span class=\"df-legend-title\">Data Flow</span>");
        for (Map.Entry<String, String> e : keyColors.entrySet()) {
            html.append("<div class=\"legend-item\">");
            html.append("<div class=\"legend-dot\" style=\"background:")
                    .append(e.getValue())
                    .append("\"></div>");
            html.append("<span>").append(esc(e.getKey())).append("</span>");
            html.append("</div>\n");
        }
        html.append("<button class=\"df-toggle\" onclick=\"toggleDataFlow()\">Toggle Edges</button>");
        html.append("</div>\n");
    }

    private void appendLegend(StringBuilder html) {
        Set<AgenticSystemTopology> present = new LinkedHashSet<>();
        collectTopologies(rootAgent, present);

        html.append("<div class=\"legend\">\n");
        for (AgenticSystemTopology t : present) {
            html.append("<div class=\"legend-item\">");
            html.append("<div class=\"legend-dot\" style=\"background:")
                    .append(color(t))
                    .append("\"></div>");
            html.append("<span>").append(label(t)).append("</span>");
            html.append("</div>\n");
        }
        html.append("</div>\n");
    }

    private static void collectTopologies(AgentInstance agent, Set<AgenticSystemTopology> topologies) {
        topologies.add(agent.topology());
        if (agent.subagents() != null) {
            for (AgentInstance child : agent.subagents()) {
                collectTopologies(child, topologies);
            }
        }
    }

    private void appendTopologyNode(
            StringBuilder html, AgentInstance agent, String condition, Map<String, String> keyColors) {
        html.append("<li>\n");

        if (condition != null && !condition.isEmpty()) {
            html.append("<div class=\"condition-label\">when: ")
                    .append(esc(condition))
                    .append("</div>\n");
        }

        String css = cssCls(agent.topology());
        html.append("<div class=\"node-card node-border-").append(css).append("\"");

        // Data attributes for data-flow edge drawing
        html.append(" data-agent-id=\"").append(esc(agent.agentId())).append("\"");
        if (agent.outputKey() != null && !agent.outputKey().isEmpty()) {
            html.append(" data-output-key=\"").append(esc(agent.outputKey())).append("\"");
            html.append(" data-output-color=\"")
                    .append(keyColors.getOrDefault(agent.outputKey(), "#999"))
                    .append("\"");
        }
        if (agent.arguments() != null && !agent.arguments().isEmpty()) {
            String inputKeys = agent.arguments().stream()
                    .map(AgentArgument::name)
                    .reduce((a, b) -> a + "," + b)
                    .orElse("");
            html.append(" data-input-keys=\"").append(esc(inputKeys)).append("\"");
        }
        html.append(">\n");

        // Header
        html.append("<div class=\"node-card-header\">");
        html.append("<span class=\"topology-badge ").append(css).append("\">");
        html.append(label(agent.topology())).append("</span>");
        html.append("<span class=\"agent-name\">").append(esc(agent.name())).append("</span>");
        if (agent.async()) {
            html.append("<span class=\"async-badge\">async</span>");
        }
        html.append("</div>\n");

        // Details
        html.append("<div class=\"node-details\">\n");
        if (agent.type() != null) {
            String typeName = agent.type().getSimpleName();
            if (!typeName.equals(agent.name())) {
                detail(html, "Type", typeName);
            }
        }
        if (agent.description() != null && !agent.description().isEmpty()) {
            detail(html, "Desc", truncate(agent.description(), 60));
        }
        if (agent.outputType() != null) {
            detail(html, "Returns", simpleTypeName(agent.outputType()));
        }
        if (agent.plannerType() != null) {
            detail(html, "Planner", agent.plannerType().getSimpleName());
        }
        appendLoopInfo(html, agent);
        html.append("</div>\n"); // node-details

        // Data-flow key badges (colored pills showing what this agent reads/writes)
        appendKeyBadges(html, agent, keyColors);

        html.append("</div>\n"); // node-card

        // Children
        List<AgentInstance> children = agent.subagents();
        if (children != null && !children.isEmpty()) {
            Map<String, String> conds = conditionsOf(agent);
            html.append("<ul>\n");
            for (AgentInstance child : children) {
                appendTopologyNode(html, child, conds.get(child.agentId()), keyColors);
            }
            html.append("</ul>\n");
        }

        html.append("</li>\n");
    }

    private void appendKeyBadges(StringBuilder html, AgentInstance agent, Map<String, String> keyColors) {
        boolean hasInputs = agent.arguments() != null && !agent.arguments().isEmpty();
        boolean hasOutput = agent.outputKey() != null && !agent.outputKey().isEmpty();
        if (!hasInputs && !hasOutput) return;

        html.append("<div class=\"key-flow\">");
        if (hasInputs) {
            for (AgentArgument arg : agent.arguments()) {
                String c = keyColors.getOrDefault(arg.name(), "#999");
                html.append("<span class=\"key-pill key-in\" style=\"--kc:")
                        .append(c)
                        .append("\">");
                html.append("&#8592; ").append(esc(arg.name()));
                html.append("</span>");
            }
        }
        if (hasOutput) {
            String c = keyColors.getOrDefault(agent.outputKey(), "#999");
            html.append("<span class=\"key-pill key-out\" style=\"--kc:")
                    .append(c)
                    .append("\">");
            html.append("&#8594; ").append(esc(agent.outputKey()));
            html.append("</span>");
        }
        html.append("</div>\n");
    }

    private void appendLoopInfo(StringBuilder html, AgentInstance agent) {
        if (agent.topology() != AgenticSystemTopology.LOOP) {
            return;
        }
        LoopAgentInstance loop = agent.as(LoopAgentInstance.class);
        html.append("<div class=\"loop-info\">");
        html.append("<span class=\"loop-tag\">max ")
                .append(loop.maxIterations())
                .append("</span>");
        if (loop.exitCondition() != null && !loop.exitCondition().isEmpty()) {
            html.append("<span class=\"loop-tag\">exit: ")
                    .append(esc(truncate(loop.exitCondition(), 40)))
                    .append("</span>");
        }
        html.append("<span class=\"loop-tag\">")
                .append(loop.testExitAtLoopEnd() ? "test at end" : "test at start")
                .append("</span>");
        html.append("</div>\n");
    }

    private Map<String, String> conditionsOf(AgentInstance agent) {
        if (agent.topology() != AgenticSystemTopology.ROUTER) {
            return Map.of();
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (ConditionalAgent ca : agent.as(ConditionalAgentInstance.class).conditionalSubagents()) {
            if (ca.condition() != null) {
                for (AgentInstance child : ca.agentInstances()) {
                    map.put(child.agentId(), ca.condition());
                }
            }
        }
        return map;
    }

    // -----------------------------------------------------------------------
    // Executions
    // -----------------------------------------------------------------------

    private void appendExecutionsSection(StringBuilder html) {
        Set<Object> memoryIds;
        if (memoryId != null) {
            memoryIds = monitor.allMemoryIds().contains(memoryId) ? Set.of(memoryId) : Set.of();
        } else {
            memoryIds = monitor.allMemoryIds();
        }

        html.append("<section class=\"section\">\n");
        html.append("<div class=\"section-header\">\n");
        html.append("  <div class=\"section-icon\">").append(ICON_EXEC).append("</div>\n");
        html.append("  <h2 class=\"section-title\">Execution History</h2>\n");
        html.append("  <span class=\"section-count\">").append(memoryIds.size()).append(" session(s)</span>\n");
        html.append("</div>\n");

        if (memoryIds.isEmpty()) {
            html.append("<div class=\"no-data\">No executions recorded.</div>\n");
        } else {
            int idx = 0;
            for (Object mid : memoryIds) {
                appendExecutionGroup(html, mid, idx++);
            }
        }
        html.append("</section>\n");
    }

    private void appendExecutionGroup(StringBuilder html, Object memoryId, int idx) {
        List<MonitoredExecution> all = monitor.allExecutionsFor(memoryId);
        String gid = "g" + idx;
        boolean collapsed = idx > 0;

        long successCount = all.stream().filter(e -> statusOf(e).equals("ok")).count();
        long failCount = all.stream().filter(e -> statusOf(e).equals("fail")).count();
        long runCount = all.stream().filter(e -> statusOf(e).equals("run")).count();

        html.append("<div class=\"execution-group\">\n");

        // Header
        html.append("<div class=\"execution-header");
        if (collapsed) html.append(" collapsed");
        html.append("\" onclick=\"toggle('").append(gid).append("')\">\n");
        html.append("  <span class=\"chevron\">&#9660;</span>");
        html.append("  <span class=\"execution-memory-id\">Session: ")
                .append(esc(String.valueOf(memoryId)))
                .append("</span>");
        html.append("  <div class=\"execution-meta\">");
        html.append("    <span class=\"section-count\">").append(all.size()).append(" run(s)</span>");
        if (successCount > 0)
            html.append("<span class=\"status-badge st-ok\">")
                    .append(successCount)
                    .append(" success</span>");
        if (failCount > 0)
            html.append("<span class=\"status-badge st-fail\">")
                    .append(failCount)
                    .append(" failed</span>");
        if (runCount > 0) html.append("<span class=\"status-badge st-run\">in progress</span>");
        html.append("  </div>\n");
        html.append("</div>\n");

        // Body
        html.append("<div id=\"").append(gid).append("\" class=\"execution-body");
        if (collapsed) html.append(" collapsed");
        html.append("\">\n");

        for (MonitoredExecution e : all) {
            appendExecution(html, e);
        }

        html.append("</div>\n");
        html.append("</div>\n");
    }

    private static String statusOf(MonitoredExecution exec) {
        if (exec.hasError()) return "fail";
        if (exec.done()) return "ok";
        return "run";
    }

    private void appendExecution(StringBuilder html, MonitoredExecution exec) {
        String status = statusOf(exec);
        AgentInvocation top = exec.topLevelInvocations();

        html.append("<div class=\"single-exec\">\n");

        // Summary line
        html.append("<div class=\"exec-summary\">");
        html.append("<span class=\"status-dot st-dot-").append(status).append("\"></span>");
        html.append("<span class=\"exec-agent\">")
                .append(esc(top.agent().name()))
                .append("</span>");
        if (top.done()) {
            html.append("<span class=\"dur-badge\">")
                    .append(fmtDur(top.duration()))
                    .append("</span>");
            html.append("<span class=\"exec-time\">").append(top.startTime().format(TIME_FMT));
            html.append(" &#8594; ").append(top.finishTime().format(TIME_FMT)).append("</span>");
        } else {
            html.append("<span class=\"dur-badge\">running...</span>");
        }
        html.append("</div>\n");

        // Error
        if (exec.hasError()) {
            html.append("<div class=\"error-box\">");
            html.append("<strong>Error in ")
                    .append(esc(exec.error().agentName()))
                    .append(":</strong> ");
            html.append("<code>")
                    .append(esc(String.valueOf(exec.error().error().getMessage())))
                    .append("</code>");
            html.append("</div>\n");
        }

        // Waterfall
        if (top.done() || !top.nestedInvocations().isEmpty()) {
            LocalDateTime base = top.startTime();
            long totalMs = top.done()
                    ? Math.max(1, top.duration().toMillis())
                    : Math.max(1, Duration.between(base, LocalDateTime.now()).toMillis());

            html.append("<table class=\"wf-table\">\n");
            html.append(
                    "<thead><tr><th>Agent</th><th>Duration</th><th class=\"wf-timeline-col\">Timeline</th><th>Input</th><th>Output</th></tr></thead>\n");
            html.append("<tbody>\n");
            appendWfRow(html, top, 0, base, totalMs);
            html.append("</tbody></table>\n");
        }

        html.append("</div>\n");
    }

    private void appendWfRow(StringBuilder html, AgentInvocation inv, int depth, LocalDateTime base, long totalMs) {
        AgentInstance ag = inv.agent();
        String css = cssCls(ag.topology());

        long offMs = Duration.between(base, inv.startTime()).toMillis();
        long durMs = inv.done()
                ? inv.duration().toMillis()
                : Duration.between(inv.startTime(), LocalDateTime.now()).toMillis();
        double leftPct = Math.max(0, (double) offMs / totalMs * 100.0);
        double widthPct = Math.max(0.4, (double) durMs / totalMs * 100.0);

        html.append("<tr>");

        // Agent column
        html.append("<td><div class=\"wf-agent\">");
        for (int i = 0; i < depth; i++) html.append("<span class=\"wf-indent\"></span>");
        if (depth > 0) html.append("<span class=\"wf-connector\">&#x2514;</span>");
        html.append("<span class=\"topology-badge sm ")
                .append(css)
                .append("\">")
                .append(label(ag.topology()))
                .append("</span>");
        html.append(" ").append(esc(ag.name()));
        if (inv.iterationIndex() >= 0) {
            html.append(" <span class=\"iter-tag\">iter ")
                    .append(inv.iterationIndex())
                    .append("</span>");
        }
        html.append("</div></td>");

        // Duration column
        html.append("<td class=\"wf-dur\">");
        html.append(inv.done() ? fmtDur(inv.duration()) : "<em>...</em>");
        html.append("</td>");

        // Timeline bar column
        html.append("<td><div class=\"wf-bar-track\">");
        html.append("<div class=\"wf-bar bar-")
                .append(css)
                .append("\" style=\"left:")
                .append(fmt(leftPct))
                .append("%;width:")
                .append(fmt(widthPct))
                .append("%;\"");
        html.append(" title=\"")
                .append(esc(ag.name()))
                .append(": ")
                .append(inv.done() ? fmtDur(inv.duration()) : "running")
                .append("\">");
        html.append("</div></div></td>");

        // Input column
        html.append("<td class=\"wf-io\">");
        if (inv.inputs() != null && !inv.inputs().isEmpty()) {
            appendTruncatedWithTooltip(html, mapToString(inv.inputs()), 35);
        }
        html.append("</td>");

        // Output column
        html.append("<td class=\"wf-io\">");
        if (inv.done() && inv.output() != null) {
            appendTruncatedWithTooltip(html, String.valueOf(inv.output()), 35);
        }
        html.append("</td>");

        html.append("</tr>\n");

        for (AgentInvocation nested : inv.nestedInvocations()) {
            appendWfRow(html, nested, depth + 1, base, totalMs);
        }
    }

    private void appendTruncatedWithTooltip(StringBuilder html, String text, int max) {
        String display = truncate(text, max);
        html.append("<span class=\"tt-trigger\">");
        html.append(esc(display));
        if (text.length() > max) {
            html.append("<span class=\"tt-content\">").append(esc(text)).append("</span>");
        }
        html.append("</span>");
    }

    // -----------------------------------------------------------------------
    // Footer
    // -----------------------------------------------------------------------

    private void appendFooter(StringBuilder html) {
        html.append("<footer class=\"footer\">");
        html.append("Generated by LangChain4j at ");
        html.append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        html.append("</footer>\n");
    }

    // -----------------------------------------------------------------------
    // Script
    // -----------------------------------------------------------------------

    private void appendScript(StringBuilder html) {
        html.append(
                """
                <script>
                function toggle(id) {
                    var body = document.getElementById(id);
                    body.classList.toggle('collapsed');
                    body.previousElementSibling.classList.toggle('collapsed');
                }

                function toggleDataFlow() {
                    var svg = document.getElementById('df-svg');
                    if (!svg) return;
                    svg.classList.toggle('df-hidden');
                }

                function drawDataFlow() {
                    var svg = document.getElementById('df-svg');
                    var tree = svg ? svg.parentElement : null;
                    if (!svg || !tree) return;

                    // Size SVG to match tree
                    svg.style.width = tree.scrollWidth + 'px';
                    svg.style.height = tree.scrollHeight + 'px';

                    // Remove old paths
                    svg.querySelectorAll('.df-path').forEach(function(p) { p.remove(); });

                    var treeRect = tree.getBoundingClientRect();

                    // Build producer map: outputKey -> [node, ...]
                    var prodMap = {};
                    tree.querySelectorAll('.node-card[data-output-key]').forEach(function(n) {
                        var k = n.getAttribute('data-output-key');
                        if (!prodMap[k]) prodMap[k] = [];
                        prodMap[k].push(n);
                    });

                    // For each consumer node, draw edges from matching producers
                    tree.querySelectorAll('.node-card[data-input-keys]').forEach(function(consumer) {
                        var keys = consumer.getAttribute('data-input-keys').split(',');
                        var cRect = consumer.getBoundingClientRect();
                        var cx = cRect.left + cRect.width / 2 - treeRect.left;
                        var cy = cRect.top - treeRect.top;

                        keys.forEach(function(key) {
                            var producers = prodMap[key];
                            if (!producers) return;
                            producers.forEach(function(producer) {
                                if (producer === consumer) return;
                                // Skip if producer is an ancestor or descendant (structural edge)
                                if (producer.contains(consumer) || consumer.contains(producer)) return;

                                var color = producer.getAttribute('data-output-color') || '#999';
                                var pRect = producer.getBoundingClientRect();
                                var px = pRect.left + pRect.width / 2 - treeRect.left;
                                var py = pRect.bottom - treeRect.top;

                                var markerId = 'ah-' + Math.abs(hashCode(key));
                                drawEdge(svg, px, py, cx, cy, color, markerId);
                            });
                        });
                    });
                }

                function drawEdge(svg, x1, y1, x2, y2, color, markerId) {
                    var dx = x2 - x1;
                    var dy = y2 - y1;
                    var dist = Math.sqrt(dx*dx + dy*dy);
                    // Control point offset perpendicular to the line
                    var bend = Math.min(60, dist * 0.3);
                    // Offset to the right of the direction vector
                    var nx = -dy / (dist || 1) * bend;
                    var ny =  dx / (dist || 1) * bend;
                    var cpx = (x1 + x2) / 2 + nx;
                    var cpy = (y1 + y2) / 2 + ny;

                    var path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
                    path.setAttribute('d', 'M' + x1 + ',' + y1 + ' Q' + cpx + ',' + cpy + ' ' + x2 + ',' + y2);
                    path.setAttribute('stroke', color);
                    path.setAttribute('stroke-width', '1.8');
                    path.setAttribute('stroke-dasharray', '6,4');
                    path.setAttribute('fill', 'none');
                    path.setAttribute('opacity', '0.55');
                    path.setAttribute('class', 'df-path');
                    path.setAttribute('marker-end', 'url(#' + markerId + ')');
                    svg.appendChild(path);
                }

                function hashCode(s) {
                    var h = 0;
                    for (var i = 0; i < s.length; i++) {
                        h = ((h << 5) - h) + s.charCodeAt(i);
                        h |= 0;
                    }
                    return h;
                }

                document.addEventListener('DOMContentLoaded', drawDataFlow);
                window.addEventListener('resize', drawDataFlow);
                </script>
                """);
    }

    // -----------------------------------------------------------------------
    // Utility helpers
    // -----------------------------------------------------------------------

    private static void detail(StringBuilder html, String label, String value) {
        html.append("<div class=\"node-detail\">");
        html.append("<span class=\"nd-label\">").append(label).append("</span> ");
        html.append("<span class=\"nd-value\">").append(esc(value)).append("</span>");
        html.append("</div>\n");
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private static String fmtDur(Duration d) {
        long ms = d.toMillis();
        if (ms < 1000) return ms + "ms";
        if (ms < 60_000) return String.format("%.1fs", ms / 1000.0);
        return String.format("%dm %ds", ms / 60_000, (ms % 60_000) / 1000);
    }

    private static String fmt(double v) {
        return String.format("%.1f", v);
    }

    private static String simpleTypeName(Type type) {
        if (type instanceof Class<?> c) return c.getSimpleName();
        String n = type.getTypeName();
        int dot = n.lastIndexOf('.');
        return dot >= 0 ? n.substring(dot + 1) : n;
    }

    private static String mapToString(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(e.getKey()).append("=").append(truncate(String.valueOf(e.getValue()), 60));
        }
        return sb.append("}").toString();
    }

    // -----------------------------------------------------------------------
    // Topology helpers
    // -----------------------------------------------------------------------

    private static String cssCls(AgenticSystemTopology t) {
        return switch (t) {
            case AI_AGENT -> "ai";
            case NON_AI_AGENT -> "nonai";
            case HUMAN_IN_THE_LOOP -> "human";
            case SEQUENCE -> "seq";
            case PARALLEL -> "par";
            case LOOP -> "loop";
            case ROUTER -> "rtr";
            case STAR -> "star";
        };
    }

    private static String label(AgenticSystemTopology t) {
        return switch (t) {
            case AI_AGENT -> "AI";
            case NON_AI_AGENT -> "Action";
            case HUMAN_IN_THE_LOOP -> "Human";
            case SEQUENCE -> "Sequence";
            case PARALLEL -> "Parallel";
            case LOOP -> "Loop";
            case ROUTER -> "Router";
            case STAR -> "Star";
        };
    }

    private static String color(AgenticSystemTopology t) {
        return switch (t) {
            case AI_AGENT -> "#2e8555";
            case NON_AI_AGENT -> "#6b7280";
            case HUMAN_IN_THE_LOOP -> "#d97706";
            case SEQUENCE -> "#0891b2";
            case PARALLEL -> "#3b82f6";
            case LOOP -> "#7c3aed";
            case ROUTER -> "#dc2626";
            case STAR -> "#ca8a04";
        };
    }

    // -----------------------------------------------------------------------
    // Data-flow key helpers
    // -----------------------------------------------------------------------

    private static final String[] KEY_PALETTE = {
        "#e63946", "#457b9d", "#2a9d8f", "#e9c46a", "#7b2d8e",
        "#f4a261", "#264653", "#d62828", "#6a994e", "#bc6c25"
    };

    private Map<String, String> collectStateKeyColors(AgentInstance root) {
        Set<String> keys = new LinkedHashSet<>();
        collectKeys(root, keys);
        Map<String, String> colors = new LinkedHashMap<>();
        int i = 0;
        for (String key : keys) {
            colors.put(key, KEY_PALETTE[i % KEY_PALETTE.length]);
            i++;
        }
        return colors;
    }

    private void collectKeys(AgentInstance agent, Set<String> keys) {
        if (agent.outputKey() != null && !agent.outputKey().isEmpty()) {
            keys.add(agent.outputKey());
        }
        if (agent.arguments() != null) {
            for (AgentArgument arg : agent.arguments()) {
                if (arg.name() != null) {
                    keys.add(arg.name());
                }
            }
        }
        if (agent.subagents() != null) {
            for (AgentInstance child : agent.subagents()) {
                collectKeys(child, keys);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Inline SVG icons (small, no external deps)
    // -----------------------------------------------------------------------

    private static final String LOGO_SVG = "<svg width=\"28\" height=\"28\" viewBox=\"0 0 28 28\" fill=\"none\">"
            + "<rect width=\"28\" height=\"28\" rx=\"6\" fill=\"white\" fill-opacity=\"0.2\"/>"
            + "<path d=\"M7 8h14M7 14h14M7 20h10\" stroke=\"white\" stroke-width=\"2\" stroke-linecap=\"round\"/>"
            + "</svg>";

    private static final String ICON_TOPOLOGY = "<svg width=\"16\" height=\"16\" viewBox=\"0 0 16 16\" fill=\"none\">"
            + "<circle cx=\"8\" cy=\"3\" r=\"2\" fill=\"white\"/>"
            + "<circle cx=\"4\" cy=\"13\" r=\"2\" fill=\"white\"/>"
            + "<circle cx=\"12\" cy=\"13\" r=\"2\" fill=\"white\"/>"
            + "<line x1=\"8\" y1=\"5\" x2=\"4\" y2=\"11\" stroke=\"white\" stroke-width=\"1.5\"/>"
            + "<line x1=\"8\" y1=\"5\" x2=\"12\" y2=\"11\" stroke=\"white\" stroke-width=\"1.5\"/>"
            + "</svg>";

    private static final String ICON_EXEC = "<svg width=\"16\" height=\"16\" viewBox=\"0 0 16 16\" fill=\"none\">"
            + "<rect x=\"1\" y=\"3\" width=\"14\" height=\"2\" rx=\"1\" fill=\"white\"/>"
            + "<rect x=\"1\" y=\"7\" width=\"10\" height=\"2\" rx=\"1\" fill=\"white\"/>"
            + "<rect x=\"1\" y=\"11\" width=\"12\" height=\"2\" rx=\"1\" fill=\"white\"/>"
            + "</svg>";

    // -----------------------------------------------------------------------
    // CSS (embedded)
    // -----------------------------------------------------------------------

    private static final String CSS =
            """
            :root {
                --green: #2e8555;
                --green-dk: #205d3b;
                --green-lt: #3cad6e;
                --teal: #25c2a0;
                --c-ai: #2e8555;
                --c-nonai: #6b7280;
                --c-human: #d97706;
                --c-seq: #0891b2;
                --c-par: #3b82f6;
                --c-loop: #7c3aed;
                --c-rtr: #dc2626;
                --c-star: #ca8a04;
                --bg: #f8faf9;
                --bg2: #f0f4f2;
                --fg: #1a1a2e;
                --fg2: #4a5568;
                --fg3: #718096;
                --brd: #e2e8f0;
                --conn: #cbd5e1;
                --rad: 8px;
            }
            * { margin:0; padding:0; box-sizing:border-box; }
            body { font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;
                   background:var(--bg); color:var(--fg); line-height:1.6; }

            /* ---- Navbar ---- */
            .navbar { background:linear-gradient(135deg,var(--green-dk),var(--green));
                      color:#fff; padding:14px 32px; display:flex; align-items:center; gap:14px;
                      box-shadow:0 2px 8px rgba(0,0,0,.15); position:sticky; top:0; z-index:100; }
            .navbar-logo { display:flex; align-items:center; justify-content:center; }
            .navbar-title { font-size:20px; font-weight:600; letter-spacing:-.3px; }
            .navbar-subtitle { font-size:13px; opacity:.75; margin-left:auto; }

            /* ---- Container ---- */
            .container { max-width:1440px; margin:0 auto; padding:32px; }

            /* ---- Section ---- */
            .section { margin-bottom:40px; }
            .section-header { display:flex; align-items:center; gap:12px; margin-bottom:16px;
                              padding-bottom:10px; border-bottom:2px solid var(--green); }
            .section-icon { width:30px; height:30px; background:var(--green); border-radius:var(--rad);
                            display:flex; align-items:center; justify-content:center; flex-shrink:0; }
            .section-title { font-size:21px; font-weight:600; }
            .section-count { font-size:12px; color:var(--fg3); background:var(--bg2);
                             padding:2px 10px; border-radius:12px; }

            /* ---- Legend ---- */
            .legend { display:flex; flex-wrap:wrap; gap:14px; margin-bottom:18px;
                      padding:10px 16px; background:#fff; border:1px solid var(--brd); border-radius:var(--rad); }
            .legend-item { display:flex; align-items:center; gap:5px; font-size:12px; color:var(--fg2); }
            .legend-dot { width:12px; height:12px; border-radius:3px; }

            /* ---- Org tree (top-down CSS connector chart) ---- */
            .topology-scroll { overflow-x:auto; padding:16px 0; }
            .org-tree { text-align:center; }
            .org-tree ul { display:flex; justify-content:center; padding-top:24px;
                           position:relative; list-style:none; }
            .org-tree > ul { padding-top:0; }
            .org-tree li { display:flex; flex-direction:column; align-items:center;
                           position:relative; padding:24px 10px 0; }
            .org-tree > ul > li { padding-top:0; }

            /* vertical line from parent ul down to horizontal bar */
            .org-tree ul::before { content:''; position:absolute; top:0; left:50%;
                                   border-left:2px solid var(--conn); height:24px; }
            .org-tree > ul::before { display:none; }

            /* horizontal bar + vertical drop per child */
            .org-tree li::before, .org-tree li::after {
                content:''; position:absolute; top:0; width:50%; height:24px;
                border-top:2px solid var(--conn); }
            .org-tree li::before { right:50%; }
            .org-tree li::after  { left:50%; border-left:2px solid var(--conn); }
            .org-tree > ul > li::before, .org-tree > ul > li::after { display:none; }
            .org-tree li:first-child::before { border-top:none; }
            .org-tree li:last-child::after   { border-top:none; }
            .org-tree li:only-child::before  { display:none; }
            .org-tree li:only-child::after   { border-top:none; }

            /* ---- Node card ---- */
            .node-card { position:relative; background:#fff; border:1px solid var(--brd);
                         border-radius:var(--rad); padding:10px 14px; min-width:140px; max-width:260px;
                         box-shadow:0 1px 3px rgba(0,0,0,.06); transition:box-shadow .2s,transform .2s; text-align:left; }
            .node-card:hover { box-shadow:0 4px 14px rgba(0,0,0,.1); transform:translateY(-2px); }
            .node-card-header { display:flex; align-items:center; gap:6px; margin-bottom:4px; flex-wrap:wrap; }
            .agent-name { font-size:14px; font-weight:600; }

            .topology-badge { display:inline-block; padding:1px 7px; border-radius:4px;
                              font-size:10px; font-weight:700; text-transform:uppercase;
                              letter-spacing:.4px; color:#fff; }
            .topology-badge.sm { font-size:9px; padding:0 5px; }
            .topology-badge.ai    { background:var(--c-ai); }
            .topology-badge.nonai { background:var(--c-nonai); }
            .topology-badge.human { background:var(--c-human); }
            .topology-badge.seq   { background:var(--c-seq); }
            .topology-badge.par   { background:var(--c-par); }
            .topology-badge.loop  { background:var(--c-loop); }
            .topology-badge.rtr   { background:var(--c-rtr); }
            .topology-badge.star  { background:var(--c-star); }

            .node-border-ai    { border-left:3px solid var(--c-ai); }
            .node-border-nonai { border-left:3px solid var(--c-nonai); }
            .node-border-human { border-left:3px solid var(--c-human); }
            .node-border-seq   { border-left:3px solid var(--c-seq); }
            .node-border-par   { border-left:3px solid var(--c-par); }
            .node-border-loop  { border-left:3px solid var(--c-loop); }
            .node-border-rtr   { border-left:3px solid var(--c-rtr); }
            .node-border-star  { border-left:3px solid var(--c-star); }

            .async-badge { font-size:9px; background:#fef3c7; color:#92400e; padding:0 5px;
                           border-radius:3px; border:1px solid #fcd34d; }

            .node-details { font-size:11px; }
            .node-detail { display:flex; gap:4px; margin-top:1px; }
            .nd-label { color:var(--fg3); font-weight:500; }
            .nd-value { color:var(--fg2); }

            .condition-label { font-size:9px; background:#fef3c7; color:#92400e;
                               padding:1px 8px; border-radius:3px; border:1px solid #fcd34d;
                               margin-bottom:4px; white-space:nowrap; }

            .loop-info { display:flex; gap:6px; margin-top:4px; flex-wrap:wrap; }
            .loop-tag { font-size:10px; background:#ede9fe; color:#5b21b6;
                        padding:0 6px; border-radius:3px; border:1px solid #c4b5fd; }

            /* ---- Data-flow key badges ---- */
            .key-flow { display:flex; flex-wrap:wrap; gap:3px; margin-top:5px;
                        padding-top:5px; border-top:1px dashed var(--brd); }
            .key-pill { font-size:9px; font-weight:600; padding:0 6px; border-radius:3px;
                        border:1px solid var(--kc);
                        background:#fff; color:var(--kc); white-space:nowrap;
                        opacity:.85; }

            /* ---- Data-flow SVG overlay ---- */
            .df-svg { position:absolute; top:0; left:0; pointer-events:none; z-index:2; overflow:visible; }
            .df-svg.df-hidden { display:none; }

            /* ---- Data-flow legend ---- */
            .df-legend { align-items:center; }
            .df-legend-title { font-size:12px; font-weight:600; color:var(--fg); margin-right:4px; }
            .df-toggle { margin-left:auto; font-size:11px; padding:3px 12px; border-radius:4px;
                         border:1px solid var(--brd); background:var(--bg2); color:var(--fg2);
                         cursor:pointer; transition:background .15s; }
            .df-toggle:hover { background:var(--brd); }

            /* ---- Execution groups ---- */
            .execution-group { margin-bottom:12px; }
            .execution-header { display:flex; align-items:center; gap:10px; padding:10px 16px;
                                background:#fff; border:1px solid var(--brd);
                                border-radius:var(--rad) var(--rad) 0 0;
                                cursor:pointer; user-select:none; transition:background .15s; }
            .execution-header:hover { background:var(--bg2); }
            .execution-header.collapsed { border-radius:var(--rad); }
            .chevron { transition:transform .2s; color:var(--fg3); font-size:11px; }
            .execution-header.collapsed .chevron { transform:rotate(-90deg); }
            .execution-memory-id { font-weight:600; font-size:14px; }
            .execution-meta { margin-left:auto; display:flex; gap:8px; align-items:center; }

            .status-badge { padding:2px 10px; border-radius:12px; font-size:11px; font-weight:600; }
            .st-ok   { background:#d1fae5; color:#065f46; }
            .st-fail { background:#fee2e2; color:#991b1b; }
            .st-run  { background:#dbeafe; color:#1e40af; }

            .execution-body { border:1px solid var(--brd); border-top:none;
                              border-radius:0 0 var(--rad) var(--rad); background:#fff; overflow:hidden; }
            .execution-body.collapsed { display:none; }

            /* ---- Single execution ---- */
            .single-exec { padding:12px 16px; border-bottom:1px solid var(--brd); }
            .single-exec:last-child { border-bottom:none; }
            .exec-summary { display:flex; align-items:center; gap:10px; margin-bottom:8px; flex-wrap:wrap; }
            .status-dot { width:8px; height:8px; border-radius:50%; flex-shrink:0; }
            .st-dot-ok   { background:#10b981; }
            .st-dot-fail { background:#ef4444; }
            .st-dot-run  { background:#3b82f6; animation:pulse 1.5s infinite; }
            @keyframes pulse { 0%,100%{opacity:1} 50%{opacity:.4} }
            .exec-agent { font-weight:600; font-size:14px; }
            .dur-badge { font-size:12px; color:var(--fg3); font-family:'SF Mono','Fira Code',monospace; }
            .exec-time { font-size:11px; color:var(--fg3); }

            .error-box { background:#fef2f2; border:1px solid #fecaca; border-radius:var(--rad);
                         padding:8px 12px; margin-bottom:8px; font-size:12px; color:#991b1b; }
            .error-box code { font-size:11px; word-break:break-all; }

            /* ---- Waterfall table ---- */
            .wf-table { width:100%; border-collapse:collapse; font-size:13px; }
            .wf-table th { text-align:left; padding:6px 10px; background:var(--bg2); color:var(--fg3);
                           font-size:10px; font-weight:600; text-transform:uppercase;
                           letter-spacing:.5px; border-bottom:1px solid var(--brd); }
            .wf-table td { padding:5px 10px; border-bottom:1px solid var(--brd); vertical-align:middle; }
            .wf-table tr:last-child td { border-bottom:none; }
            .wf-table tr:hover { background:var(--bg); }
            .wf-timeline-col { width:35%; }

            .wf-agent { display:flex; align-items:center; gap:4px; white-space:nowrap; }
            .wf-indent { display:inline-block; width:18px; }
            .wf-connector { color:var(--conn); font-family:monospace; margin-right:2px; font-size:12px; }
            .wf-dur { font-family:'SF Mono','Fira Code',monospace; font-size:12px; color:var(--fg2); white-space:nowrap; }

            .wf-bar-track { position:relative; height:18px; background:var(--bg2); border-radius:3px; min-width:120px; }
            .wf-bar { position:absolute; top:2px; height:14px; border-radius:3px; opacity:.8; min-width:2px;
                      transition:opacity .15s; }
            .wf-bar:hover { opacity:1; }
            .bar-ai    { background:var(--c-ai); }
            .bar-nonai { background:var(--c-nonai); }
            .bar-human { background:var(--c-human); }
            .bar-seq   { background:var(--c-seq); }
            .bar-par   { background:var(--c-par); }
            .bar-loop  { background:var(--c-loop); }
            .bar-rtr   { background:var(--c-rtr); }
            .bar-star  { background:var(--c-star); }

            .wf-io { font-size:11px; color:var(--fg3); max-width:200px;
                     overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }

            .iter-tag { font-size:9px; background:#ede9fe; color:#5b21b6;
                        padding:0 5px; border-radius:3px; border:1px solid #c4b5fd;
                        font-weight:600; white-space:nowrap; }

            /* ---- Tooltip ---- */
            .tt-trigger { position:relative; cursor:help; }
            .tt-trigger .tt-content { display:none; position:absolute; bottom:calc(100% + 4px); left:0;
                                      background:var(--fg); color:#fff; padding:6px 10px; border-radius:6px;
                                      font-size:11px; white-space:pre-wrap; max-width:400px; z-index:10;
                                      box-shadow:0 4px 12px rgba(0,0,0,.2); word-break:break-all; }
            .tt-trigger:hover .tt-content { display:block; }

            /* ---- Footer ---- */
            .footer { text-align:center; padding:24px 0 8px; font-size:12px; color:var(--fg3);
                      border-top:1px solid var(--brd); margin-top:20px; }
            """;
}
