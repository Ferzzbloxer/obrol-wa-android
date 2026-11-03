package obrolcore

import (
	"database/sql"
)

type Store struct {
	db *sql.DB
}

func openStore(path string) (*Store, error) {
	db, err := sql.Open("sqlite3", "file:"+path+"?_journal_mode=WAL")
	if err != nil {
		return nil, err
	}

	_, err = db.Exec(`
		CREATE TABLE IF NOT EXISTS messages (
			chat TEXT NOT NULL,
			id TEXT NOT NULL PRIMARY KEY,
			sender TEXT NOT NULL,
			text TEXT NOT NULL,
			from_me INTEGER NOT NULL,
			ts INTEGER NOT NULL,
			kind TEXT NOT NULL,
			status INTEGER NOT NULL
		);
		CREATE TABLE IF NOT EXISTS chats (
			jid TEXT PRIMARY KEY,
			name TEXT NOT NULL,
			last_text TEXT,
			last_ts INTEGER,
			unread INTEGER DEFAULT 0
		);
	`)
	return &Store{db: db}, err
}

func (s *Store) SaveMessage(chat, id, sender, text string, fromMe bool, ts int64, kind string, status int) {
	fm := 0
	if fromMe {
		fm = 1
	}
	s.db.Exec(`INSERT OR REPLACE INTO messages (chat, id, sender, text, from_me, ts, kind, status) 
               VALUES (?, ?, ?, ?, ?, ?, ?, ?)`, chat, id, sender, text, fm, ts, kind, status)
	s.db.Exec(`INSERT INTO chats (jid, name, last_text, last_ts) VALUES (?, ?, ?, ?)
               ON CONFLICT(jid) DO UPDATE SET last_text=excluded.last_text, last_ts=excluded.last_ts`,
		chat, chat, text, ts)
}