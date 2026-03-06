CREATE TABLE IF NOT EXISTS weeks (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    url TEXT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    rating NUMERIC(3,2) NOT NULL,
    avatar BLOB NOT NULL
);