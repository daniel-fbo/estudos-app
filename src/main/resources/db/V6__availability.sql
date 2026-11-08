-- P3: grade semanal de horários livres da Agenda (dia da semana 1=segunda..7=domingo, hora 0-23).
CREATE TABLE availability (
  weekday INTEGER NOT NULL,
  hour INTEGER NOT NULL,
  PRIMARY KEY (weekday, hour)
);
