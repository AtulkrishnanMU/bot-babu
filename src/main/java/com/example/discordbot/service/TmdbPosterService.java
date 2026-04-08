package com.example.discordbot.service;

import com.example.discordbot.client.TmdbApiClient;
import com.example.discordbot.client.dto.TmdbConfiguration;
import com.example.discordbot.client.dto.TmdbMovieImages;
import com.example.discordbot.client.dto.TmdbSearchResponse;
import com.example.discordbot.client.dto.TmdbMovieResult;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TmdbPosterService {

    private final TmdbApiClient tmdbApiClient;
    
    @Value("${tmdb.api.key}")
    private String tmdbApiKey;

    public Mono<MovieImagesResult> getMoviePosters(String movieTitle) {
        return searchMovie(movieTitle)
                .flatMap(this::getMoviePostersById)
                .onErrorReturn(new MovieImagesResult(movieTitle, List.of()));
    }

    public Mono<MovieImagesResult> getMovieBackdrops(String movieTitle) {
        return searchMovie(movieTitle)
                .flatMap(this::getMovieBackdropsById)
                .onErrorReturn(new MovieImagesResult(movieTitle, List.of()));
    }

    private Mono<MovieSearchResult> searchMovie(String movieTitle) {
        return tmdbApiClient.searchMovies(movieTitle, 1, "en-US", false, tmdbApiKey)
                .map(TmdbSearchResponse::getResults)
                .filter(results -> !results.isEmpty())
                .map(results -> {
                    TmdbMovieResult movie = results.get(0);
                    return new MovieSearchResult(movie.getId(), movie.getTitle());
                })
                .switchIfEmpty(Mono.error(new RuntimeException("Movie not found: " + movieTitle)));
    }

    private Mono<MovieImagesResult> getMoviePostersById(MovieSearchResult movie) {
        return tmdbApiClient.getConfiguration(tmdbApiKey)
                .zipWith(tmdbApiClient.getMovieImages(movie.getId(), "en-US", tmdbApiKey))
                .map(tuple -> {
                    TmdbConfiguration config = tuple.getT1();
                    TmdbMovieImages images = tuple.getT2();
                    List<String> imageUrls = buildImageUrls(config, images.getPosters(), "poster");
                    return new MovieImagesResult(movie.getTitle(), imageUrls);
                });
    }

    private Mono<MovieImagesResult> getMovieBackdropsById(MovieSearchResult movie) {
        return tmdbApiClient.getConfiguration(tmdbApiKey)
                .zipWith(tmdbApiClient.getMovieImages(movie.getId(), "en-US", tmdbApiKey))
                .map(tuple -> {
                    TmdbConfiguration config = tuple.getT1();
                    TmdbMovieImages images = tuple.getT2();
                    List<String> imageUrls = buildImageUrls(config, images.getBackdrops(), "backdrop");
                    return new MovieImagesResult(movie.getTitle(), imageUrls);
                });
    }

    private List<String> buildImageUrls(TmdbConfiguration config, List<TmdbMovieImages.Image> images, String type) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }

        String baseUrl = config.getImages().getSecureBaseUrl();
        List<String> sizes = type.equals("poster") ? 
            config.getImages().getPosterSizes() : 
            config.getImages().getBackdropSizes();

        // Prefer w500 for posters, w1280 for backdrops
        String preferredSize = type.equals("poster") ? "w500" : "w1280";
        
        // Fallback sizes if preferred not available
        List<String> sizePriority = List.of(preferredSize, "original", "w300", "w780");

        for (String size : sizePriority) {
            if (sizes.contains(size)) {
                String finalSize = size;
                return images.stream()
                        .map(img -> baseUrl + finalSize + img.getFilePath())
                        .collect(Collectors.toList());
            }
        }

        return List.of();
    }

    @Data
    public static class MovieImagesResult {
        private final String movieTitle;
        private final List<String> imageUrls;
    }

    @Data
    private static class MovieSearchResult {
        private final Integer id;
        private final String title;
    }
}
