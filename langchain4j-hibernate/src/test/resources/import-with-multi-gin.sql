create index if not exists generic_embedding_entity_ivfflat_index on generic_embedding_entity using ivfflat(embedding vector_cosine_ops) with (lists = 1);
-- A GIN index on the metadata text expressions requires this extension
create extension if not exists btree_gin;
create index if not exists generic_embedding_entity_metadata_key_index on generic_embedding_entity using gin((metadata#>>'{key}'));
create index if not exists generic_embedding_entity_metadata_key_index on generic_embedding_entity using gin((metadata#>>'{name}'));
create index if not exists generic_embedding_entity_metadata_key_index on generic_embedding_entity using gin((metadata#>>'{age}'));
