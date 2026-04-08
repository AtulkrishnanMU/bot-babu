package com.example.discordbot.dto;

import com.example.discordbot.model.MovieData;
import com.example.discordbot.model.Rating;

import java.util.List;

/**
 * Movie ratings result class
 */
public class MovieRatingsResult {
    private String slug;
    private MovieData movieData;
    private List<Rating> ratings;
    private int totalRatings;
    private double averageRating;
    private boolean hasNext;

    public MovieRatingsResult(String slug, MovieData movieData, List<Rating> ratings, int totalRatings, double averageRating, boolean hasNext) {
        this.slug = slug;
        this.movieData = movieData;
        this.ratings = ratings;
        this.totalRatings = totalRatings;
        this.averageRating = averageRating;
        this.hasNext = hasNext;
    }

    public String getSlug() {
        return slug;
    }

    public MovieData getMovieData() {
        return movieData;
    }

    public List<Rating> getRatings() {
        return ratings;
    }

    public int getTotalRatings() {
        return totalRatings;
    }

    public double getAverageRating() {
        return averageRating;
    }

    public boolean isHasNext() {
        return hasNext;
    }
}
