-- P1: contador manual por período e páginas lidas por período.
CREATE TABLE goal_count_log (
  id INTEGER PRIMARY KEY,
  goal_id INTEGER NOT NULL REFERENCES goal(id) ON DELETE CASCADE,
  delta INTEGER NOT NULL,
  at TEXT NOT NULL
);
CREATE INDEX idx_goal_count_log_goal ON goal_count_log(goal_id);

CREATE TABLE reading_log (
  id INTEGER PRIMARY KEY,
  book_id INTEGER NOT NULL REFERENCES book(id) ON DELETE CASCADE,
  pages INTEGER NOT NULL,
  at TEXT NOT NULL
);
CREATE INDEX idx_reading_log_book ON reading_log(book_id);
