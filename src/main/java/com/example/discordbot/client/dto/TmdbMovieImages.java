package com.example.discordbot.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class TmdbMovieImages {
    
    @JsonProperty("id")
    private Integer id;
    
    @JsonProperty("backdrops")
    private List<Image> backdrops;
    
    @JsonProperty("posters")
    private List<Image> posters;
    
    @JsonProperty("logos")
    private List<Image> logos;
    
    @Data
    public static class Image {
        @JsonProperty("file_path")
        private String filePath;
        
        @JsonProperty("width")
        private Integer width;
        
        @JsonProperty("height")
        private Integer height;
        
        @JsonProperty("aspect_ratio")
        private Double aspectRatio;
        
        @JsonProperty("vote_average")
        private Double voteAverage;
        
        @JsonProperty("vote_count")
        private Integer voteCount;
        
        @JsonProperty("iso_639_1")
        private String iso6391;
    }
}
