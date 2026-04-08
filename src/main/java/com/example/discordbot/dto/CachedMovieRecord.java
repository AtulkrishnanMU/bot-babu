package com.example.discordbot.dto;

import com.example.discordbot.model.MovieData;
import com.example.discordbot.model.Rating;

import java.util.List;

/**
 * Cached movie record class
 */
public class CachedMovieRecord {
    private MovieData movieData;
    private List<Rating> allRatings;
    
    public CachedMovieRecord(MovieData movieData, List<Rating> allRatings) {
        this.movieData = movieData;
        this.allRatings = allRatings;
    }
    
    public MovieData getMovieData() { return movieData; }
    public List<Rating> getAllRatings() { return allRatings; }
}
