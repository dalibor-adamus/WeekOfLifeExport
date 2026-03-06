CREATE TABLE IF NOT EXISTS editors_choice (
    week_id int PRIMARY KEY,
    week_name VARCHAR(100) NOT NULL,
    week_uri VARCHAR(100) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    week_date DATE,
    author VARCHAR(50) NOT NULL,
    author_name VARCHAR(100) NOT NULL,
    profession VARCHAR(50) NOT NULL,
    location VARCHAR(50) NOT NULL,
    image BLOB NOT NULL
);
