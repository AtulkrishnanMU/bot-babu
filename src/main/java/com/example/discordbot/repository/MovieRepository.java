package com.example.discordbot.repository;

import com.example.discordbot.model.Movie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MovieRepository extends JpaRepository<Movie, Long> {

    /**
     * Find movie by exact title match
     */
    Optional<Movie> findByTitleIgnoreCase(String title);

    /**
     * Find movies by fuzzy search using pg_trgm similarity
     * Returns movies where title is similar to search query, ordered by similarity score
     */
    @Query(value = """
        SELECT m.*, similarity(m.title, :query) as sim_score
        FROM movies m
        WHERE m.title % :query
        ORDER BY sim_score DESC
        LIMIT 5
        """, nativeQuery = true)
    List<Movie> findByTitleFuzzy(@Param("query") String query);

    /**
     * Find the best matching movie by fuzzy search with a minimum similarity threshold
     * Returns the single best match or empty if no match above threshold
     */
    @Query(value = """
        SELECT m.*
        FROM movies m
        WHERE m.title % :query
        ORDER BY similarity(m.title, :query) DESC
        LIMIT 1
        """, nativeQuery = true)
    Optional<Movie> findBestMatchByTitleFuzzy(@Param("query") String query);

    /**
     * Check if a movie with the given letterboxd URL already exists
     */
    boolean existsByLetterboxdUrl(String letterboxdUrl);

    /**
     * Find movie by letterboxd URL
     */
    Optional<Movie> findByLetterboxdUrl(String letterboxdUrl);
}
