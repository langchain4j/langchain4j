package dev.langchain4j.model.sensenova.chat;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class KnowledgeConfig {

	@SerializedName("control_level")
	private KnowledgeControlLevel controlLevel;

	@SerializedName("knowledge_base_result")
	private Boolean knowledgeBaseResult;

	@SerializedName("knowledge_base_configs")
	private List<KnowledgeBaseConfig> knowledgeBaseConfigs;

}
