-- Esquema inicial (V1). Datas em ISO-8601 (TEXT), UTC. Durações em segundos.
PRAGMA foreign_keys = ON;

CREATE TABLE study_session (
  id INTEGER PRIMARY KEY,
  area TEXT NOT NULL,
  subject TEXT,
  started_at TEXT NOT NULL,
  ended_at TEXT,
  duration_s INTEGER NOT NULL DEFAULT 0,
  note TEXT
);

CREATE TABLE goal (
  id INTEGER PRIMARY KEY,
  title TEXT NOT NULL,
  area TEXT NOT NULL,
  kind TEXT NOT NULL CHECK (kind IN ('HORAS','CONTAGEM')),
  period TEXT NOT NULL CHECK (period IN ('SEMANAL','MENSAL')),
  target REAL NOT NULL,
  manual_count INTEGER NOT NULL DEFAULT 0,
  auto_count_ac INTEGER NOT NULL DEFAULT 0,
  active INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL
);

CREATE TABLE code_test_case (
  id INTEGER PRIMARY KEY,
  source_path TEXT NOT NULL,
  input TEXT NOT NULL,
  expected TEXT NOT NULL,
  origin TEXT NOT NULL DEFAULT 'MANUAL'   -- MANUAL | STRESS
);

CREATE TABLE code_run (
  id INTEGER PRIMARY KEY,
  source_path TEXT NOT NULL,
  ran_at TEXT NOT NULL,
  verdict TEXT NOT NULL,                  -- AC | WA | TLE | RE | CE
  passed INTEGER NOT NULL,
  total INTEGER NOT NULL,
  detail TEXT
);

CREATE TABLE ai_call (
  id INTEGER PRIMARY KEY,
  cache_key TEXT NOT NULL,
  runner TEXT NOT NULL,                   -- CLAUDE | AGY
  model TEXT,
  task TEXT NOT NULL,                     -- AULA | DICA | CARDS | LIVRO | OUTRO
  prompt TEXT NOT NULL,
  response TEXT,
  status TEXT NOT NULL,                   -- OK | ERRO | CANCELADO
  input_tokens INTEGER,
  output_tokens INTEGER,
  cost_usd REAL,
  duration_ms INTEGER,
  created_at TEXT NOT NULL
);
CREATE INDEX ix_ai_call_key ON ai_call(cache_key);
CREATE INDEX ix_ai_call_day ON ai_call(created_at);

CREATE TABLE lesson (
  id INTEGER PRIMARY KEY,
  kind TEXT NOT NULL,                     -- aula_matematica | aula_programacao
  area TEXT NOT NULL,
  title TEXT NOT NULL,
  topics TEXT,
  status TEXT NOT NULL,                   -- GERANDO | PRONTA | FALHOU
  pdf_path TEXT,
  md_path TEXT,
  ai_call_id INTEGER REFERENCES ai_call(id),
  created_at TEXT NOT NULL
);

CREATE TABLE book (
  id INTEGER PRIMARY KEY,
  title TEXT NOT NULL,
  authors TEXT,
  area TEXT NOT NULL,
  kind TEXT NOT NULL,
  pdf_path TEXT UNIQUE,
  pages INTEGER,
  current_page INTEGER NOT NULL DEFAULT 0,
  status TEXT NOT NULL DEFAULT 'QUERO_LER',
  tags TEXT,
  notes TEXT,
  indexed_at TEXT
);

CREATE TABLE card (
  id INTEGER PRIMARY KEY,
  front TEXT NOT NULL,
  back TEXT NOT NULL,
  area TEXT NOT NULL,
  origin TEXT NOT NULL,
  source_ref TEXT,
  front_hash TEXT NOT NULL UNIQUE,
  anki_note_id INTEGER,
  synced_at TEXT
);

CREATE TABLE cf_submission (
  id INTEGER PRIMARY KEY,                 -- id da submissão no Codeforces
  contest_id INTEGER,
  problem_index TEXT,
  problem_name TEXT,
  rating INTEGER,
  tags TEXT,                              -- JSON array
  verdict TEXT,
  submitted_at TEXT NOT NULL
);

CREATE TABLE hint (
  id INTEGER PRIMARY KEY,
  problem_ref TEXT NOT NULL,
  level INTEGER NOT NULL CHECK (level BETWEEN 1 AND 3),
  text TEXT NOT NULL,
  created_at TEXT NOT NULL,
  UNIQUE (problem_ref, level)
);

CREATE TABLE calendar_block (
  id INTEGER PRIMARY KEY,
  goal_id INTEGER REFERENCES goal(id),
  google_event_id TEXT,
  starts_at TEXT NOT NULL,
  ends_at TEXT NOT NULL,
  done INTEGER NOT NULL DEFAULT 0
);
