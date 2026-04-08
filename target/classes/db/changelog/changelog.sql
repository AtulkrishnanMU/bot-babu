--liquibase formatted sql
--changeset discord-bot:001-create-movies-table

-- Enable pg_trgm extension for fuzzy search
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Create movies table
CREATE TABLE IF NOT EXISTS movies (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    letterboxd_url VARCHAR(1000) NOT NULL,
    year VARCHAR(10),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create index on title for fuzzy search using pg_trgm
CREATE INDEX IF NOT EXISTS idx_movies_title_trgm ON movies USING gin (title gin_trgm_ops);

-- Create unique index on letterboxd_url
CREATE UNIQUE INDEX IF NOT EXISTS idx_movies_letterboxd_url ON movies (letterboxd_url);

--rollback DROP TABLE IF EXISTS movies;
--rollback DROP EXTENSION IF EXISTS pg_trgm;
