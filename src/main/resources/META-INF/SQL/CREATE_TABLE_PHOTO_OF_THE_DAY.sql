CREATE TABLE IF NOT EXISTS photo_of_the_day (
    photo_date DATE PRIMARY KEY,
    author VARCHAR(50) NOT NULL,
    author_name VARCHAR(100) NOT NULL,
    image BLOB NOT NULL
);