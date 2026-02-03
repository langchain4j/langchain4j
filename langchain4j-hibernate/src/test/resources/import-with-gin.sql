create index if not exists generic_embedding_entity_ivfflat_index on generic_embedding_entity using ivfflat(embedding vector_cosine_ops) with (lists = 1);
create index if not exists generic_embedding_entity_metadata_index on generic_embedding_entity using gin(metadata);
