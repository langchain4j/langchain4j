package dev.langchain4j.model.spark.chat.entity.request;


import dev.langchain4j.model.spark.chat.entity.SparkChatParameter;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class SparkRequestParameter{

    private SparkChatParameter chat;

}
