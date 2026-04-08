package com.example.discordbot.service;

import com.example.discordbot.model.Movie;
import com.example.discordbot.repository.MovieRepository;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import serpapi.GoogleSearch;
import serpapi.SerpApiSearchException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class LetterboxdSearchService {

    @Value("${serpapi.key:}")
    private String serpApiKey;

    private final MovieRepository movieRepository;

    public LetterboxdSearchService(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    /**
     * Search for a movie's Letterboxd link.
     * First checks database with fuzzy matching, then falls back to SerpApi if not found.
     * Does NOT save to database - saving happens after scraping with proper movie details.
     */
    public Mono<String> searchLetterboxdLink(String movieTitle) {
        return Mono.fromCallable(() -> {
            log.info("Searching Letterboxd for movie: {}", movieTitle);

            // Check if the query ends in a number to avoid inaccurate fuzzy DB matching
            if (movieTitle.trim().matches(".*\\d$")) {
                log.info("Movie title '{}' ends in a number. Skipping database match and using SerpApi.", movieTitle);
                return searchWithSerpApi(movieTitle);
            }

            // Step 1: Try to find in database using fuzzy search
            Optional<Movie> dbMatch = findInDatabase(movieTitle);
            if (dbMatch.isPresent()) {
                log.info("Found movie in database: {} -> {}", movieTitle, dbMatch.get().getLetterboxdUrl());
                return dbMatch.get().getLetterboxdUrl();
            }

            log.info("Movie not found in database, querying SerpApi: {}", movieTitle);

            // Step 2: Query SerpApi if not in database
            // Note: We don't save here - saving happens after scraping with proper movie details
            return searchWithSerpApi(movieTitle);

        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Find movie in database using fuzzy matching
     */
    private Optional<Movie> findInDatabase(String movieTitle) {
        try {
            // First try exact match
            Optional<Movie> exactMatch = movieRepository.findByTitleIgnoreCase(movieTitle);
            if (exactMatch.isPresent()) {
                log.debug("Exact match found in database for: {}", movieTitle);
                return exactMatch;
            }

            // Then try fuzzy match
            List<Movie> fuzzyMatches = movieRepository.findByTitleFuzzy(movieTitle);
            if (!fuzzyMatches.isEmpty()) {
                log.debug("Fuzzy match found in database for: {} -> {}", movieTitle, fuzzyMatches.get(0).getTitle());
                return Optional.of(fuzzyMatches.get(0));
            }

            // Try best match method
            Optional<Movie> bestMatch = movieRepository.findBestMatchByTitleFuzzy(movieTitle);
            if (bestMatch.isPresent()) {
                log.debug("Best fuzzy match found in database for: {} -> {}", movieTitle, bestMatch.get().getTitle());
                return bestMatch;
            }

            return Optional.empty();
        } catch (Exception e) {
            log.error("Error searching database for movie '{}': {}", movieTitle, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Search for movie using SerpApi
     */
    private String searchWithSerpApi(String movieTitle) {
        Map<String, String> parameter = new HashMap<>();
        parameter.put("q", movieTitle + " letterboxd");
        parameter.put("hl", "en");
        parameter.put("gl", "us");
        parameter.put("google_domain", "google.com");
        parameter.put("api_key", serpApiKey);

        GoogleSearch search = new GoogleSearch(parameter);

        try {
            JsonObject results = search.getJson();

            // Get organic results
            JsonArray organicResults = results.getAsJsonArray("organic_results");
            if (organicResults == null || organicResults.size() == 0) {
                log.warn("No organic results found for: {}", movieTitle);
                return null;
            }

            // Find the first letterboxd.com link
            for (JsonElement element : organicResults) {
                JsonObject result = element.getAsJsonObject();
                String link = result.has("link") ? result.get("link").getAsString() : null;

                if (link != null && link.contains("letterboxd.com")) {
                    log.info("Found Letterboxd link via SerpApi: {}", link);
                    return link;
                }
            }

            log.warn("No Letterboxd link found in SerpApi results for: {}", movieTitle);
            return null;

        } catch (SerpApiSearchException ex) {
            log.error("SerpApi search failed: {}", ex.getMessage(), ex);
            throw new RuntimeException("Search failed: " + ex.getMessage(), ex);
        }
    }
}
