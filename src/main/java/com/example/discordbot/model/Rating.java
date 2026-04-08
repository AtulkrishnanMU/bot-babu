package com.example.discordbot.model;

/**
 * Rating class
 */
public class Rating {
    private String username;
    private int rating;
    private boolean liked;
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }
    
    public boolean isLiked() { return liked; }
    public void setLiked(boolean liked) { this.liked = liked; }
}
