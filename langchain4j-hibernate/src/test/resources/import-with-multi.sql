create index if not exists columns_embedding_entity_ivfflat_index on columns_embedding_entity using ivfflat(embedding vector_cosine_ops) with (lists = 1);
create index if not exists columns_embedding_entity_metadata_key_index on columns_embedding_entity(key);
create index if not exists columns_embedding_entity_metadata_key_index on columns_embedding_entity(name);
create index if not exists columns_embedding_entity_metadata_key_index on columns_embedding_entity(age);
