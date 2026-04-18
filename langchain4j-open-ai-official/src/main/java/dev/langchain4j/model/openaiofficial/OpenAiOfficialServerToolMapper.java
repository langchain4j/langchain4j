package dev.langchain4j.model.openaiofficial;

import com.openai.core.JsonValue;
import com.openai.models.responses.ComputerTool;
import com.openai.models.responses.ComputerUsePreviewTool;
import com.openai.models.responses.ContainerAuto;
import com.openai.models.responses.ContainerNetworkPolicyDomainSecret;
import com.openai.models.responses.ContainerReference;
import com.openai.models.responses.FileSearchTool;
import com.openai.models.responses.FunctionShellTool;
import com.openai.models.responses.InlineSkill;
import com.openai.models.responses.LocalEnvironment;
import com.openai.models.responses.LocalSkill;
import com.openai.models.responses.NamespaceTool;
import com.openai.models.responses.SkillReference;
import com.openai.models.responses.Tool;
import com.openai.models.responses.ToolSearchTool;
import com.openai.models.responses.WebSearchPreviewTool;
import com.openai.models.responses.WebSearchTool;
import dev.langchain4j.exception.UnsupportedFeatureException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class OpenAiOfficialServerToolMapper {

    private OpenAiOfficialServerToolMapper() {}

    static Tool toResponsesTool(OpenAiOfficialServerTool serverTool) {
        if (isSupportedWebSearchPreviewToolType(serverTool.type())) {
            return Tool.ofWebSearchPreview(toWebSearchPreviewTool(serverTool));
        }
        if (isSupportedWebSearchToolType(serverTool.type())) {
            return Tool.ofWebSearch(toWebSearchTool(serverTool));
        }
        if (isSupportedComputerUsePreviewToolType(serverTool.type())) {
            return Tool.ofComputerUsePreview(toComputerUsePreviewTool(serverTool));
        }

        return switch (serverTool.type()) {
            case "file_search" -> Tool.ofFileSearch(toFileSearchTool(serverTool));
            case "tool_search" -> Tool.ofSearch(toToolSearchTool(serverTool));
            case "mcp" -> Tool.ofMcp(toMcpTool(serverTool));
            case "shell" -> Tool.ofShell(toShellTool(serverTool));
            case "computer" -> Tool.ofComputer(toComputerTool(serverTool));
            case "namespace" -> Tool.ofNamespace(toNamespaceTool(serverTool));
            default ->
                throw new UnsupportedFeatureException(
                        "Unsupported OpenAI server tool type: " + serverTool.type()
                                + ". Supported types are: web_search, web_search_*, file_search, tool_search, mcp, shell, computer, computer_use_*, namespace.");
        };
    }

    private static boolean isSupportedWebSearchToolType(String type) {
        return "web_search".equals(type)
                || (type != null && type.startsWith("web_search_") && !type.startsWith("web_search_preview"));
    }

    private static boolean isSupportedWebSearchPreviewToolType(String type) {
        return type != null && type.startsWith("web_search_preview");
    }

    private static boolean isSupportedComputerUsePreviewToolType(String type) {
        return type != null && type.startsWith("computer_use_");
    }

    private static WebSearchTool toWebSearchTool(OpenAiOfficialServerTool serverTool) {
        WebSearchTool.Builder builder = WebSearchTool.builder();
        Map<String, Object> attributes = serverTool.attributes();
        rejectBlockedDomains(attributes);
        putAdditionalProperties(
                attributes,
                List.of("filters", "search_context_size", "user_location", "allowed_domains"),
                builder::putAdditionalProperty);
        builder.type(WebSearchTool.Type.of(serverTool.type()));
        String searchContextSize = stringValue(attributes, "search_context_size");
        if (searchContextSize != null) {
            builder.searchContextSize(WebSearchTool.SearchContextSize.of(searchContextSize));
        }
        Object userLocation = mapValue(attributes, "user_location");
        if (userLocation instanceof Map<?, ?> map) {
            WebSearchTool.UserLocation.Builder userLocationBuilder = WebSearchTool.UserLocation.builder();
            map.forEach((key, value) ->
                    userLocationBuilder.putAdditionalProperty(String.valueOf(key), JsonValue.from(value)));
            builder.userLocation(userLocationBuilder.build());
        }
        Object filters = filtersFromAttributes(attributes);
        if (filters instanceof Map<?, ?> map) {
            WebSearchTool.Filters.Builder filtersBuilder = WebSearchTool.Filters.builder();
            map.forEach((key, value) -> {
                if ("allowed_domains".equals(String.valueOf(key))) {
                    List<String> allowedDomains = stringListValue(value);
                    if (allowedDomains != null) {
                        filtersBuilder.allowedDomains(allowedDomains);
                    }
                } else {
                    filtersBuilder.putAdditionalProperty(String.valueOf(key), JsonValue.from(value));
                }
            });
            builder.filters(filtersBuilder.build());
        }
        return builder.build();
    }

    private static WebSearchPreviewTool toWebSearchPreviewTool(OpenAiOfficialServerTool serverTool) {
        WebSearchPreviewTool.Builder builder = WebSearchPreviewTool.builder();
        Map<String, Object> attributes = serverTool.attributes();
        rejectBlockedDomains(attributes);
        putAdditionalProperties(
                attributes,
                List.of("search_context_size", "search_content_types", "user_location"),
                builder::putAdditionalProperty);
        builder.type(WebSearchPreviewTool.Type.of(serverTool.type()));
        String searchContextSize = stringValue(attributes, "search_context_size");
        if (searchContextSize != null) {
            builder.searchContextSize(WebSearchPreviewTool.SearchContextSize.of(searchContextSize));
        }
        List<String> searchContentTypes = stringListValue(attributes.get("search_content_types"));
        if (searchContentTypes != null) {
            builder.searchContentTypes(searchContentTypes.stream()
                    .map(WebSearchPreviewTool.SearchContentType::of)
                    .toList());
        }
        Object userLocation = mapValue(attributes, "user_location");
        if (userLocation instanceof Map<?, ?> map) {
            builder.userLocation(toWebSearchPreviewUserLocation(map));
        }
        return builder.build();
    }

    private static FileSearchTool toFileSearchTool(OpenAiOfficialServerTool serverTool) {
        FileSearchTool.Builder builder = FileSearchTool.builder();
        Map<String, Object> attributes = serverTool.attributes();
        putAdditionalProperties(
                attributes,
                List.of("vector_store_ids", "filters", "max_num_results", "ranking_options"),
                builder::putAdditionalProperty);
        builder.type(JsonValue.from(serverTool.type()));
        List<String> vectorStoreIds = stringListValue(attributes.get("vector_store_ids"));
        if (vectorStoreIds != null) {
            builder.vectorStoreIds(vectorStoreIds);
        }
        Integer maxNumResults = integerValue(attributes, "max_num_results");
        if (maxNumResults != null) {
            builder.maxNumResults(maxNumResults.longValue());
        }
        Object filters = attributes.get("filters");
        if (filters instanceof Map<?, ?> map) {
            builder.filters(JsonValue.from(toStringObjectMap(map)));
        }
        Object rankingOptions = mapValue(attributes, "ranking_options");
        if (rankingOptions instanceof Map<?, ?> map) {
            FileSearchTool.RankingOptions.Builder rankingBuilder = FileSearchTool.RankingOptions.builder();
            map.forEach(
                    (key, value) -> rankingBuilder.putAdditionalProperty(String.valueOf(key), JsonValue.from(value)));
            builder.rankingOptions(rankingBuilder.build());
        }
        return builder.build();
    }

    private static ToolSearchTool toToolSearchTool(OpenAiOfficialServerTool serverTool) {
        ToolSearchTool.Builder builder = ToolSearchTool.builder();
        Map<String, Object> attributes = serverTool.attributes();
        putAdditionalProperties(
                attributes, List.of("description", "execution", "parameters"), builder::putAdditionalProperty);
        builder.type(JsonValue.from(serverTool.type()));
        String description = stringValue(attributes, "description");
        if (description != null) {
            builder.description(description);
        }
        String execution = stringValue(attributes, "execution");
        if (execution != null) {
            builder.execution(ToolSearchTool.Execution.of(execution));
        }
        Object parameters = attributes.get("parameters");
        if (parameters != null) {
            builder.parameters(JsonValue.from(parameters));
        }
        return builder.build();
    }

    private static Tool.Mcp toMcpTool(OpenAiOfficialServerTool serverTool) {
        Tool.Mcp.Builder builder = Tool.Mcp.builder();
        Map<String, Object> attributes = serverTool.attributes();
        putAdditionalProperties(
                attributes,
                List.of(
                        "server_label",
                        "allowed_tools",
                        "authorization",
                        "connector_id",
                        "defer_loading",
                        "headers",
                        "require_approval",
                        "server_description",
                        "server_url"),
                builder::putAdditionalProperty);
        builder.type(JsonValue.from(serverTool.type()));
        String serverLabel = stringValue(attributes, "server_label");
        if (serverLabel != null) {
            builder.serverLabel(serverLabel);
        } else if (serverTool.name() != null) {
            builder.serverLabel(serverTool.name());
        }
        String authorization = stringValue(attributes, "authorization");
        if (authorization != null) {
            builder.authorization(authorization);
        }
        String connectorId = stringValue(attributes, "connector_id");
        if (connectorId != null) {
            builder.connectorId(Tool.Mcp.ConnectorId.of(connectorId));
        }
        Boolean deferLoading = booleanValue(attributes, "defer_loading");
        if (deferLoading != null) {
            builder.deferLoading(deferLoading);
        }
        String serverDescription = stringValue(attributes, "server_description");
        if (serverDescription != null) {
            builder.serverDescription(serverDescription);
        }
        String serverUrl = stringValue(attributes, "server_url");
        if (serverUrl != null) {
            builder.serverUrl(serverUrl);
        }
        Object allowedTools = attributes.get("allowed_tools");
        if (allowedTools instanceof List<?> list) {
            builder.allowedToolsOfMcp(list.stream().map(String::valueOf).toList());
        } else if (allowedTools instanceof Map<?, ?> map) {
            builder.allowedTools(
                    Tool.Mcp.AllowedTools.ofMcpToolFilter(toMcpAllowedToolsFilter(toStringObjectMap(map))));
        }
        Object headers = mapValue(attributes, "headers");
        if (headers instanceof Map<?, ?> map) {
            Tool.Mcp.Headers.Builder headersBuilder = Tool.Mcp.Headers.builder();
            map.forEach(
                    (key, value) -> headersBuilder.putAdditionalProperty(String.valueOf(key), JsonValue.from(value)));
            builder.headers(headersBuilder.build());
        }
        Object requireApproval = attributes.get("require_approval");
        if (requireApproval instanceof String value) {
            builder.requireApproval(Tool.Mcp.RequireApproval.ofMcpToolApprovalSetting(
                    Tool.Mcp.RequireApproval.McpToolApprovalSetting.of(value)));
        } else if (requireApproval instanceof Map<?, ?> map) {
            builder.requireApproval(Tool.Mcp.RequireApproval.ofMcpToolApprovalFilter(
                    toMcpRequireApprovalFilter(toStringObjectMap(map))));
        }
        return builder.build();
    }

    private static Tool.Mcp.AllowedTools.McpToolFilter toMcpAllowedToolsFilter(Map<String, Object> allowedTools) {
        Tool.Mcp.AllowedTools.McpToolFilter.Builder builder = Tool.Mcp.AllowedTools.McpToolFilter.builder();
        putAdditionalProperties(allowedTools, List.of("read_only", "tool_names"), builder::putAdditionalProperty);
        if (allowedTools.containsKey("read_only")) {
            builder.readOnly(Boolean.parseBoolean(String.valueOf(allowedTools.get("read_only"))));
        }
        List<String> toolNames = stringListValue(allowedTools.get("tool_names"));
        if (toolNames != null) {
            builder.toolNames(toolNames);
        }
        return builder.build();
    }

    private static Tool.Mcp.RequireApproval.McpToolApprovalFilter toMcpRequireApprovalFilter(
            Map<String, Object> requireApproval) {
        Tool.Mcp.RequireApproval.McpToolApprovalFilter.Builder builder =
                Tool.Mcp.RequireApproval.McpToolApprovalFilter.builder();
        putAdditionalProperties(requireApproval, List.of("always", "never"), builder::putAdditionalProperty);
        Object always = requireApproval.get("always");
        if (always instanceof Map<?, ?> map) {
            builder.always(toMcpApprovalFilterAlways(toStringObjectMap(map)));
        }
        Object never = requireApproval.get("never");
        if (never instanceof Map<?, ?> map) {
            builder.never(toMcpApprovalFilterNever(toStringObjectMap(map)));
        }
        return builder.build();
    }

    private static Tool.Mcp.RequireApproval.McpToolApprovalFilter.Always toMcpApprovalFilterAlways(
            Map<String, Object> filter) {
        Tool.Mcp.RequireApproval.McpToolApprovalFilter.Always.Builder builder =
                Tool.Mcp.RequireApproval.McpToolApprovalFilter.Always.builder();
        putAdditionalProperties(filter, List.of("read_only", "tool_names"), builder::putAdditionalProperty);
        if (filter.containsKey("read_only")) {
            builder.readOnly(Boolean.parseBoolean(String.valueOf(filter.get("read_only"))));
        }
        List<String> toolNames = stringListValue(filter.get("tool_names"));
        if (toolNames != null) {
            builder.toolNames(toolNames);
        }
        return builder.build();
    }

    private static Tool.Mcp.RequireApproval.McpToolApprovalFilter.Never toMcpApprovalFilterNever(
            Map<String, Object> filter) {
        Tool.Mcp.RequireApproval.McpToolApprovalFilter.Never.Builder builder =
                Tool.Mcp.RequireApproval.McpToolApprovalFilter.Never.builder();
        putAdditionalProperties(filter, List.of("read_only", "tool_names"), builder::putAdditionalProperty);
        if (filter.containsKey("read_only")) {
            builder.readOnly(Boolean.parseBoolean(String.valueOf(filter.get("read_only"))));
        }
        List<String> toolNames = stringListValue(filter.get("tool_names"));
        if (toolNames != null) {
            builder.toolNames(toolNames);
        }
        return builder.build();
    }

    private static FunctionShellTool toShellTool(OpenAiOfficialServerTool serverTool) {
        FunctionShellTool.Builder builder = FunctionShellTool.builder();
        Map<String, Object> attributes = serverTool.attributes();
        putAdditionalProperties(attributes, List.of("environment"), builder::putAdditionalProperty);
        builder.type(JsonValue.from(serverTool.type()));

        Object environment = mapValue(attributes, "environment");
        if (environment instanceof Map<?, ?> map) {
            builder.environment(toShellEnvironment(map));
        }

        return builder.build();
    }

    private static FunctionShellTool.Environment toShellEnvironment(Map<?, ?> environment) {
        Map<String, Object> environmentMap = toStringObjectMap(environment);
        String type = String.valueOf(environmentMap.get("type"));

        return switch (type) {
            case "local" -> FunctionShellTool.Environment.ofLocal(toLocalEnvironment(environmentMap));
            case "container_auto" -> FunctionShellTool.Environment.ofContainerAuto(toContainerAuto(environmentMap));
            case "container_reference" ->
                FunctionShellTool.Environment.ofContainerReference(toContainerReference(environmentMap));
            default -> throw new IllegalArgumentException("Unsupported shell environment type: " + type);
        };
    }

    private static LocalEnvironment toLocalEnvironment(Map<String, Object> environment) {
        LocalEnvironment.Builder builder = LocalEnvironment.builder();
        putAdditionalProperties(environment, List.of("type", "skills"), builder::putAdditionalProperty);
        builder.type(JsonValue.from(environment.getOrDefault("type", "local")));

        Object skills = environment.get("skills");
        if (skills instanceof List<?> list) {
            for (Object skill : list) {
                if (skill instanceof Map<?, ?> skillMap) {
                    builder.addSkill(toLocalSkill(skillMap));
                }
            }
        }

        return builder.build();
    }

    private static LocalSkill toLocalSkill(Map<?, ?> skill) {
        Map<String, Object> skillMap = toStringObjectMap(skill);
        LocalSkill.Builder builder = LocalSkill.builder();
        putAdditionalProperties(skillMap, List.of("name", "path", "description"), builder::putAdditionalProperty);
        if (skillMap.containsKey("name")) {
            builder.name(String.valueOf(skillMap.get("name")));
        }
        if (skillMap.containsKey("path")) {
            builder.path(String.valueOf(skillMap.get("path")));
        }
        if (skillMap.containsKey("description")) {
            builder.description(String.valueOf(skillMap.get("description")));
        }
        return builder.build();
    }

    private static ContainerAuto toContainerAuto(Map<String, Object> environment) {
        ContainerAuto.Builder builder = ContainerAuto.builder();
        putAdditionalProperties(
                environment,
                List.of("type", "file_ids", "memory_limit", "network_policy", "skills"),
                builder::putAdditionalProperty);
        builder.type(JsonValue.from(environment.getOrDefault("type", "container_auto")));

        Object fileIds = environment.get("file_ids");
        if (fileIds instanceof List<?> ids) {
            builder.fileIds(ids.stream().map(String::valueOf).toList());
        }
        if (environment.containsKey("memory_limit")) {
            builder.memoryLimit(ContainerAuto.MemoryLimit.of(String.valueOf(environment.get("memory_limit"))));
        }
        Object networkPolicy = environment.get("network_policy");
        if (networkPolicy instanceof Map<?, ?> map) {
            builder.networkPolicy(toContainerNetworkPolicy(map));
        }
        Object skills = environment.get("skills");
        if (skills instanceof List<?> list) {
            for (Object skill : list) {
                if (skill instanceof Map<?, ?> skillMap) {
                    builder.addSkill(toContainerAutoSkill(skillMap));
                }
            }
        }

        return builder.build();
    }

    private static ContainerAuto.Skill toContainerAutoSkill(Map<?, ?> skill) {
        Map<String, Object> skillMap = toStringObjectMap(skill);
        String type = String.valueOf(skillMap.get("type"));
        return switch (type) {
            case "skill_reference" -> ContainerAuto.Skill.ofReference(toSkillReference(skillMap));
            case "inline" -> ContainerAuto.Skill.ofInline(toInlineSkill(skillMap));
            default -> throw new IllegalArgumentException("Unsupported shell container skill type: " + type);
        };
    }

    private static SkillReference toSkillReference(Map<String, Object> skill) {
        SkillReference.Builder builder = SkillReference.builder();
        putAdditionalProperties(skill, List.of("type", "skill_id", "version"), builder::putAdditionalProperty);
        builder.type(JsonValue.from(skill.getOrDefault("type", "skill_reference")));
        if (skill.containsKey("skill_id")) {
            builder.skillId(String.valueOf(skill.get("skill_id")));
        }
        if (skill.containsKey("version")) {
            builder.version(String.valueOf(skill.get("version")));
        }
        return builder.build();
    }

    private static ComputerUsePreviewTool toComputerUsePreviewTool(OpenAiOfficialServerTool serverTool) {
        ComputerUsePreviewTool.Builder builder = ComputerUsePreviewTool.builder();
        Map<String, Object> attributes = serverTool.attributes();
        putAdditionalProperties(
                attributes, List.of("display_height", "display_width", "environment"), builder::putAdditionalProperty);
        builder.type(JsonValue.from(serverTool.type()));

        Integer displayHeight = integerValue(attributes, "display_height");
        if (displayHeight != null) {
            builder.displayHeight(displayHeight.longValue());
        }

        Integer displayWidth = integerValue(attributes, "display_width");
        if (displayWidth != null) {
            builder.displayWidth(displayWidth.longValue());
        }

        String environment = stringValue(attributes, "environment");
        if (environment != null) {
            builder.environment(ComputerUsePreviewTool.Environment.of(environment));
        }

        return builder.build();
    }

    private static InlineSkill toInlineSkill(Map<String, Object> skill) {
        InlineSkill.Builder builder = InlineSkill.builder();
        putAdditionalProperties(
                skill, List.of("type", "name", "description", "source"), builder::putAdditionalProperty);
        builder.type(JsonValue.from(skill.getOrDefault("type", "inline")));
        if (skill.containsKey("name")) {
            builder.name(String.valueOf(skill.get("name")));
        }
        if (skill.containsKey("description")) {
            builder.description(String.valueOf(skill.get("description")));
        }
        Object source = skill.get("source");
        if (source instanceof Map<?, ?> map) {
            builder.source(toInlineSkillSource(map));
        }
        return builder.build();
    }

    private static ContainerAuto.NetworkPolicy toContainerNetworkPolicy(Map<?, ?> networkPolicy) {
        Map<String, Object> networkPolicyMap = toStringObjectMap(networkPolicy);
        String type = String.valueOf(networkPolicyMap.get("type"));
        return switch (type) {
            case "allowlist" -> ContainerAuto.NetworkPolicy.ofAllowlist(toContainerAllowlist(networkPolicyMap));
            case "disabled" ->
                ContainerAuto.NetworkPolicy.ofDisabled(
                        com.openai.models.responses.ContainerNetworkPolicyDisabled.builder()
                                .type(JsonValue.from(type))
                                .build());
            default -> throw new IllegalArgumentException("Unsupported shell container network_policy type: " + type);
        };
    }

    private static com.openai.models.responses.ContainerNetworkPolicyAllowlist toContainerAllowlist(
            Map<String, Object> networkPolicy) {
        com.openai.models.responses.ContainerNetworkPolicyAllowlist.Builder builder =
                com.openai.models.responses.ContainerNetworkPolicyAllowlist.builder();
        putAdditionalProperties(
                networkPolicy, List.of("type", "allowed_domains", "domain_secrets"), builder::putAdditionalProperty);
        builder.type(JsonValue.from(networkPolicy.getOrDefault("type", "allowlist")));
        Object allowedDomains = networkPolicy.get("allowed_domains");
        if (allowedDomains instanceof List<?> domains) {
            builder.allowedDomains(domains.stream().map(String::valueOf).toList());
        }
        Object domainSecrets = networkPolicy.get("domain_secrets");
        if (domainSecrets instanceof List<?> secrets) {
            for (Object secret : secrets) {
                if (secret instanceof Map<?, ?> secretMap) {
                    builder.addDomainSecret(toContainerNetworkPolicyDomainSecret(toStringObjectMap(secretMap)));
                }
            }
        }
        return builder.build();
    }

    private static ContainerNetworkPolicyDomainSecret toContainerNetworkPolicyDomainSecret(
            Map<String, Object> domainSecret) {
        ContainerNetworkPolicyDomainSecret.Builder builder = ContainerNetworkPolicyDomainSecret.builder();
        putAdditionalProperties(domainSecret, List.of("domain", "name", "value"), builder::putAdditionalProperty);
        if (domainSecret.containsKey("domain")) {
            builder.domain(String.valueOf(domainSecret.get("domain")));
        }
        if (domainSecret.containsKey("name")) {
            builder.name(String.valueOf(domainSecret.get("name")));
        }
        if (domainSecret.containsKey("value")) {
            builder.value(String.valueOf(domainSecret.get("value")));
        }
        return builder.build();
    }

    private static com.openai.models.responses.InlineSkillSource toInlineSkillSource(Map<?, ?> source) {
        Map<String, Object> sourceMap = toStringObjectMap(source);
        com.openai.models.responses.InlineSkillSource.Builder builder =
                com.openai.models.responses.InlineSkillSource.builder();
        putAdditionalProperties(sourceMap, List.of("type", "media_type", "data"), builder::putAdditionalProperty);
        if (sourceMap.containsKey("type")) {
            builder.type(JsonValue.from(sourceMap.get("type")));
        }
        if (sourceMap.containsKey("media_type")) {
            builder.mediaType(JsonValue.from(sourceMap.get("media_type")));
        }
        if (sourceMap.containsKey("data")) {
            builder.data(String.valueOf(sourceMap.get("data")));
        }
        return builder.build();
    }

    private static ContainerReference toContainerReference(Map<String, Object> environment) {
        ContainerReference.Builder builder = ContainerReference.builder();
        putAdditionalProperties(environment, List.of("type", "container_id"), builder::putAdditionalProperty);
        builder.type(JsonValue.from(environment.getOrDefault("type", "container_reference")));
        if (environment.containsKey("container_id")) {
            builder.containerId(String.valueOf(environment.get("container_id")));
        }
        return builder.build();
    }

    private static ComputerTool toComputerTool(OpenAiOfficialServerTool serverTool) {
        ComputerTool.Builder builder = ComputerTool.builder();
        putAdditionalProperties(serverTool.attributes(), List.of(), builder::putAdditionalProperty);
        builder.type(JsonValue.from(serverTool.type()));
        return builder.build();
    }

    private static NamespaceTool toNamespaceTool(OpenAiOfficialServerTool serverTool) {
        NamespaceTool.Builder builder = NamespaceTool.builder();
        Map<String, Object> attributes = serverTool.attributes();
        putAdditionalProperties(attributes, List.of("description", "tools"), builder::putAdditionalProperty);
        builder.type(JsonValue.from(serverTool.type()));
        if (serverTool.name() != null) {
            builder.name(serverTool.name());
        }
        String description = stringValue(attributes, "description");
        if (description != null) {
            builder.description(description);
        }

        Object tools = attributes.get("tools");
        if (tools instanceof List<?> list) {
            for (Object tool : list) {
                if (tool instanceof Map<?, ?> toolMap) {
                    builder.addTool(toNamespaceToolEntry(toolMap));
                }
            }
        }

        return builder.build();
    }

    private static WebSearchPreviewTool.UserLocation toWebSearchPreviewUserLocation(Map<?, ?> userLocation) {
        Map<String, Object> userLocationMap = toStringObjectMap(userLocation);
        WebSearchPreviewTool.UserLocation.Builder builder = WebSearchPreviewTool.UserLocation.builder();
        putAdditionalProperties(
                userLocationMap,
                List.of("type", "city", "country", "region", "timezone"),
                builder::putAdditionalProperty);
        if (userLocationMap.containsKey("type")) {
            builder.type(JsonValue.from(userLocationMap.get("type")));
        }
        if (userLocationMap.containsKey("city")) {
            builder.city(String.valueOf(userLocationMap.get("city")));
        }
        if (userLocationMap.containsKey("country")) {
            builder.country(String.valueOf(userLocationMap.get("country")));
        }
        if (userLocationMap.containsKey("region")) {
            builder.region(String.valueOf(userLocationMap.get("region")));
        }
        if (userLocationMap.containsKey("timezone")) {
            builder.timezone(String.valueOf(userLocationMap.get("timezone")));
        }
        return builder.build();
    }

    private static void rejectBlockedDomains(Map<String, Object> attributes) {
        if (attributes.containsKey("blocked_domains")) {
            throw new UnsupportedFeatureException(
                    "'blocked_domains' is not supported by OpenAI Responses web search tools. Use 'allowed_domains' instead.");
        }
    }

    private static NamespaceTool.Tool toNamespaceToolEntry(Map<?, ?> tool) {
        Map<String, Object> toolMap = toStringObjectMap(tool);
        String type = String.valueOf(toolMap.get("type"));
        return switch (type) {
            case "function" -> NamespaceTool.Tool.ofFunction(toNamespaceFunctionTool(toolMap));
            default -> throw new IllegalArgumentException("Unsupported namespace nested tool type: " + type);
        };
    }

    private static NamespaceTool.Tool.Function toNamespaceFunctionTool(Map<String, Object> tool) {
        NamespaceTool.Tool.Function.Builder builder = NamespaceTool.Tool.Function.builder();
        putAdditionalProperties(
                tool, List.of("name", "type", "description", "parameters", "strict"), builder::putAdditionalProperty);
        builder.type(JsonValue.from(tool.getOrDefault("type", "function")));
        if (tool.containsKey("name")) {
            builder.name(String.valueOf(tool.get("name")));
        }
        if (tool.containsKey("description")) {
            builder.description(String.valueOf(tool.get("description")));
        }
        if (tool.containsKey("parameters")) {
            builder.parameters(JsonValue.from(tool.get("parameters")));
        }
        if (tool.containsKey("strict")) {
            builder.strict(Boolean.parseBoolean(String.valueOf(tool.get("strict"))));
        }
        return builder.build();
    }

    private static void putAdditionalProperties(
            Map<String, Object> attributes, List<String> handledKeys, AdditionalPropertyWriter writer) {
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            if (!handledKeys.contains(entry.getKey())) {
                writer.write(entry.getKey(), JsonValue.from(entry.getValue()));
            }
        }
    }

    private static String stringValue(Map<String, Object> attributes, String key) {
        Object value = attributes.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static Integer integerValue(Map<String, Object> attributes, String key) {
        Object value = attributes.get(key);
        return value instanceof Number number ? number.intValue() : null;
    }

    private static Boolean booleanValue(Map<String, Object> attributes, String key) {
        Object value = attributes.get(key);
        return value == null ? null : Boolean.parseBoolean(String.valueOf(value));
    }

    private static Map<String, Object> mapValue(Map<String, Object> attributes, String key) {
        Object value = attributes.get(key);
        return value instanceof Map<?, ?> map ? toStringObjectMap(map) : null;
    }

    private static List<String> stringListValue(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return null;
    }

    private static Object filtersFromAttributes(Map<String, Object> attributes) {
        if (attributes.containsKey("filters")) {
            return attributes.get("filters");
        }
        Map<String, Object> filters = new LinkedHashMap<>();
        if (attributes.containsKey("allowed_domains")) {
            filters.put("allowed_domains", attributes.get("allowed_domains"));
        }
        if (attributes.containsKey("blocked_domains")) {
            filters.put("blocked_domains", attributes.get("blocked_domains"));
        }
        return filters.isEmpty() ? null : filters;
    }

    private static Map<String, Object> toStringObjectMap(Map<?, ?> source) {
        Map<String, Object> target = new LinkedHashMap<>();
        source.forEach((key, value) -> target.put(String.valueOf(key), value));
        return target;
    }

    private interface AdditionalPropertyWriter {
        void write(String key, JsonValue value);
    }
}
