CREATE TABLE IF NOT EXISTS week_comments (
    week_id INTEGER NOT NULL,
    create_date DATETIME NOT NULL,
    user TEXT NOT NULL,
    user_name TEXT NOT NULL,
    comment TEXT NOT NULL
);