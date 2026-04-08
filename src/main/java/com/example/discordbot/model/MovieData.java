package com.example.discordbot.model;

import com.example.discordbot.model.CastMember;
import com.example.discordbot.model.ReleaseDate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Movie data class
 */
public class MovieData {
    private String title;
    private String originalTitle;
    private String year;
    private String synopsis;
    private String filmId;
    private String slug;
    private String url;
    private String posterUrl;
    private String runtime;
    private Double rating;
    private Integer ratingCount;
    private List<CastMember> cast = new ArrayList<>();
    private Map<String, List<String>> crew = new HashMap<>();
    private List<String> genres = new ArrayList<>();
    private List<String> studios = new ArrayList<>();
    private List<String> countries = new ArrayList<>();
    private List<String> languages = new ArrayList<>();
    private List<ReleaseDate> releaseDates = new ArrayList<>();

    // Getters and Setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getOriginalTitle() { return originalTitle; }
    public void setOriginalTitle(String originalTitle) { this.originalTitle = originalTitle; }
    
    public String getYear() { return year; }
    public void setYear(String year) { this.year = year; }
    
    public String getSynopsis() { return synopsis; }
    public void setSynopsis(String synopsis) { this.synopsis = synopsis; }
    
    public String getFilmId() { return filmId; }
    public void setFilmId(String filmId) { this.filmId = filmId; }
    
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    
    public String getPosterUrl() { return posterUrl; }
    public void setPosterUrl(String posterUrl) { this.posterUrl = posterUrl; }
    
    public String getRuntime() { return runtime; }
    public void setRuntime(String runtime) { this.runtime = runtime; }
    
    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }
    
    public Integer getRatingCount() { return ratingCount; }
    public void setRatingCount(Integer ratingCount) { this.ratingCount = ratingCount; }
    
    public List<CastMember> getCast() { return cast; }
    public void setCast(List<CastMember> cast) { this.cast = cast; }
    
    public Map<String, List<String>> getCrew() { return crew; }
    public void setCrew(Map<String, List<String>> crew) { this.crew = crew; }
    
    public List<String> getGenres() { return genres; }
    public void setGenres(List<String> genres) { this.genres = genres; }
    
    public List<String> getStudios() { return studios; }
    public void setStudios(List<String> studios) { this.studios = studios; }
    
    public List<String> getCountries() { return countries; }
    public void setCountries(List<String> countries) { this.countries = countries; }
    
    public List<String> getLanguages() { return languages; }
    public void setLanguages(List<String> languages) { this.languages = languages; }
    
    public List<ReleaseDate> getReleaseDates() { return releaseDates; }
    public void setReleaseDates(List<ReleaseDate> releaseDates) { this.releaseDates = releaseDates; }
}
