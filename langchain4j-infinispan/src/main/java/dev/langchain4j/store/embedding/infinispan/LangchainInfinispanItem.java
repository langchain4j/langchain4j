package dev.langchain4j.store.embedding.infinispan;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class LangchainInfinispanItem {

   private String id;

   private float[] floatVector;

   private String text;

   private List<String> metadataKeys;

   private List<String> metadataValues;

   public LangchainInfinispanItem(String id, float[] floatVector, String text, List<String> metadataKeys, List<String> metadataValues) {
      this.id = id;
      this.floatVector = floatVector;
      this.text = text;
      this.metadataKeys = metadataKeys;
      this.metadataValues = metadataValues;
   }

   /**
    * the id of the embedding
    *
    * @return id
    */
   public String getId() {
      return id;
   }

   /**
    * Vector
    *
    * @return the vector
    */
   public float[] getFloatVector() {
      return floatVector;
   }

   /**
    * Maps to the text segment text
    *
    * @return text
    */
   public String getText() {
      return text;
   }

   /**
    * Maps to the text segment metadata keys
    *
    * @return metadata keys
    */
   public List<String> getMetadataKeys() {
      return metadataKeys;
   }

   /**
    * Maps to the text segment metadata values
    *
    * @return metadata values
    */
   public List<String> getMetadataValues() {
      return metadataValues;
   }

   @Override
   public String toString() {
      return "LangchainInfinispanItem{" + "id='" + id + '\'' + ", floatVector=" + Arrays.toString(floatVector)
            + ", text='" + text + '\'' + ", metadataKeys=" + metadataKeys + ", metadataValues=" + metadataValues + '}';
   }

   @Override
   public boolean equals(Object o) {
      if (this == o)
         return true;
      if (o == null || getClass() != o.getClass())
         return false;
      LangchainInfinispanItem that = (LangchainInfinispanItem) o;
      return Objects.equals(id, that.id) && Arrays.equals(floatVector, that.floatVector) && Objects.equals(text,
            that.text) && Objects.equals(metadataKeys, that.metadataKeys) && Objects.equals(metadataValues,
            that.metadataValues);
   }

   @Override
   public int hashCode() {
      int result = Objects.hash(id, text, metadataKeys, metadataValues);
      result = 31 * result + Arrays.hashCode(floatVector);
      return result;
   }
}

