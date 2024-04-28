package dev.langchain4j.model.sensenova.chat;

import com.google.gson.annotations.SerializedName;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBaseConfig {

	@SerializedName("know_id")
	private String knowId;

	/**
	 * (0, 1)
	 */
	@SerializedName("faq_threshold")
	private float faqThreshold;
}
