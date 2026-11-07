-- Respostas servidas do cache também são registradas (taxa de acerto), mas não contam no orçamento.
ALTER TABLE ai_call ADD COLUMN cache_hit INTEGER NOT NULL DEFAULT 0;
