package com.example.discordbot.client;

import com.example.discordbot.client.dto.TmdbConfiguration;
import com.example.discordbot.client.dto.TmdbMovieImages;
import com.example.discordbot.client.dto.TmdbSearchResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import reactor.core.publisher.Mono;

@HttpExchange("/3")
public interface TmdbApiClient {

    @GetExchange("/search/movie")
    Mono<TmdbSearchResponse> searchMovies(
            @RequestParam("query") String query,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "language", defaultValue = "en-US") String language,
            @RequestParam(value = "include_adult", defaultValue = "false") boolean includeAdult,
            @RequestParam("api_key") String apiKey
    );



    @GetExchange("/movie/{movie_id}/images")
    Mono<TmdbMovieImages> getMovieImages(
            @PathVariable("movie_id") Integer movieId,
            @RequestParam(value = "language", defaultValue = "en-US") String language,
            @RequestParam("api_key") String apiKey
    );

    @GetExchange("/configuration")
    Mono<TmdbConfiguration> getConfiguration(
            @RequestParam("api_key") String apiKey
    );
}
