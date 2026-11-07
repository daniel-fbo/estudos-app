CREATE TABLE cf_rating (
  contest_id INTEGER PRIMARY KEY,
  contest_name TEXT NOT NULL,
  rated_at TEXT NOT NULL,
  old_rating INTEGER NOT NULL,
  new_rating INTEGER NOT NULL
);
