package dev.langchain4j.model.sensenova.chat;

import com.google.gson.annotations.SerializedName;
import lombok.*;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class KnowledgeBaseResultDetail {

	private Float score;

	private String result;

	@SerializedName("extra_info")
	private KnowledgeBaseResultExtra extraInfo;
}
