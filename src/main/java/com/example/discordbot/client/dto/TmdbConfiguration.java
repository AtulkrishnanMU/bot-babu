package com.example.discordbot.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class TmdbConfiguration {
    
    @JsonProperty("images")
    private ImageConfiguration images;
    
    @Data
    public static class ImageConfiguration {
        @JsonProperty("base_url")
        private String baseUrl;
        
        @JsonProperty("secure_base_url")
        private String secureBaseUrl;
        
        @JsonProperty("backdrop_sizes")
        private List<String> backdropSizes;
        
        @JsonProperty("logo_sizes")
        private List<String> logoSizes;
        
        @JsonProperty("poster_sizes")
        private List<String> posterSizes;
        
        @JsonProperty("profile_sizes")
        private List<String> profileSizes;
        
        @JsonProperty("still_sizes")
        private List<String> stillSizes;
    }
}
