package com.example.discordbot.discord.embed;

import com.example.discordbot.dto.MovieRatingsResult;
import com.example.discordbot.model.MovieData;
import com.example.discordbot.model.Rating;
import com.example.discordbot.service.LetterboxdScraperService;
import com.example.discordbot.utils.JsonUtil;
import com.example.discordbot.utils.TemplateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class MovieEmbedBuilder {

    private final TemplateUtil templateUtil;
    private final JsonUtil jsonUtil;

    public MessageEmbed buildDetailedMovieEmbed(MovieData movie) {
        Map<String, Object> data = new HashMap<>();
        data.put("title", movie.getTitle());
        data.put("year", movie.getYear());
        data.put("url", movie.getUrl());
        data.put("synopsis", movie.getSynopsis());
        data.put("posterUrl", movie.getPosterUrl());
        data.put("timestamp", Instant.now().toString());
        
        // Debug logging for poster
        log.info("Building embed for movie: {}, posterUrl: {}", movie.getTitle(), movie.getPosterUrl());
        
        // Rating (keep in 10-point scale and format vote count)
        if (movie.getRating() > 0) {
            data.put("rating", String.format("%.1f", movie.getRating()));
        }
        data.put("ratingCount", movie.getRatingCount());
        data.put("ratingCountFormatted", formatVoteCount(movie.getRatingCount()));
        
        // Director from crew
        if (movie.getCrew() != null && movie.getCrew().containsKey("Director")) {
            List<String> directors = movie.getCrew().get("Director");
            data.put("director", String.join(", ", directors));
        }
        
        // Genres
        if (movie.getGenres() != null && !movie.getGenres().isEmpty()) {
            data.put("genres", String.join(", ", movie.getGenres()));
        }
        
        // Runtime
        data.put("runtime", movie.getRuntime());
        
        // Language
        if (movie.getLanguages() != null && !movie.getLanguages().isEmpty()) {
            data.put("language", String.join(", ", movie.getLanguages()));
        }
        
        // Country
        if (movie.getCountries() != null && !movie.getCountries().isEmpty()) {
            data.put("country", String.join(", ", movie.getCountries()));
        }
        
        // Release Dates (first 3)
        if (movie.getReleaseDates() != null && !movie.getReleaseDates().isEmpty()) {
            String releaseDates = movie.getReleaseDates().stream()
                    .limit(3)
                    .map(rd -> {
                        StringBuilder sb = new StringBuilder(rd.getDate());
                        if (rd.getCountry() != null && !rd.getCountry().isEmpty()) {
                            sb.append(" (").append(rd.getCountry()).append(")");
                        }
                        if (rd.getType() != null && !rd.getType().isEmpty()) {
                            sb.append(" - ").append(rd.getType());
                        }
                        return sb.toString();
                    })
                    .collect(Collectors.joining("\n"));
            data.put("releaseDates", releaseDates);
        }
        
        // Cast (first 5 actors)
        if (movie.getCast() != null && !movie.getCast().isEmpty()) {
            String cast = movie.getCast().stream()
                    .limit(5)
                    .map(c -> c.getActor() + (c.getCharacter() != null && !c.getCharacter().isEmpty() 
                            ? " (" + c.getCharacter() + ")" : ""))
                    .collect(Collectors.joining(", "));
            data.put("cast", cast);
        }

        String json = templateUtil.renderTemplate("templates/movie-embed.json.mustache", data);
        log.info("Rendered template JSON: {}", json);
        return buildEmbedFromJson(json);
    }

    private MessageEmbed buildEmbedFromJson(String json) {
        EmbedBuilder embed = new EmbedBuilder();
        
        JsonUtil.SimpleJsonObject jsonObj = jsonUtil.parse(json);
        
        if (jsonObj.has("title")) {
            String title = jsonObj.getString("title");
            String url = jsonObj.has("url") ? jsonObj.getString("url") : null;
            embed.setTitle(title, url);
        }
        
        if (jsonObj.has("description")) {
            embed.setDescription(jsonObj.getString("description"));
        }
        
        if (jsonObj.has("color")) {
            embed.setColor(new Color(jsonObj.getInt("color")));
        }
        
        if (jsonObj.has("thumbnail")) {
            JsonUtil.SimpleJsonObject thumb = jsonObj.getObject("thumbnail");
            if (thumb.has("url")) {
                String thumbnailUrl = thumb.getString("url");
                log.info("Setting thumbnail from JSON: {}", thumbnailUrl);
                embed.setThumbnail(thumbnailUrl);
            } else {
                log.warn("Thumbnail object found but no URL field");
            }
        } else {
            log.warn("No thumbnail field in JSON object");
        }
        
        if (jsonObj.has("fields")) {
            List<JsonUtil.SimpleJsonObject> fields = jsonObj.getArray("fields");
            for (JsonUtil.SimpleJsonObject field : fields) {
                embed.addField(
                    field.getString("name"),
                    field.getString("value"),
                    field.getBoolean("inline")
                );
            }
        }
        
        if (jsonObj.has("footer")) {
            JsonUtil.SimpleJsonObject footer = jsonObj.getObject("footer");
            embed.setFooter(footer.getString("text"), null);
        }
        
        if (jsonObj.has("timestamp")) {
            embed.setTimestamp(Instant.parse(jsonObj.getString("timestamp")));
        }
        
        return embed.build();
    }

    private String formatVoteCount(Integer count) {
        if (count == null || count < 1000) {
            return String.valueOf(count);
        } else if (count < 1_000_000) {
            return String.format("%.1fk", count / 1000.0);
        } else if (count < 1_000_000_000) {
            return String.format("%.1fm", count / 1_000_000.0);
        } else {
            return String.format("%.1fb", count / 1_000_000_000.0);
        }
    }

    private String formatUsdAmount(Long amount) {
        if (amount == null || amount <= 0) {
            return null;
        }
        
        // International numbering system for USD (k, m, b)
        if (amount >= 1_000_000_000) {
            return "$" + String.format("%.1fb", amount / 1_000_000_000.0);
        } else if (amount >= 1_000_000) {
            return "$" + String.format("%.1fm", amount / 1_000_000.0);
        } else if (amount >= 1_000) {
            return "$" + String.format("%dk", amount / 1_000);
        }
        return "$" + String.format("%,d", amount);
    }

    public MessageEmbed buildErrorEmbed(String title, String description) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("❌ " + title);
        embed.setDescription(description);
        embed.setColor(Color.RED);
        embed.setTimestamp(Instant.now());
        return embed.build();
    }

    public MessageEmbed buildNotFoundEmbed(String movieTitle) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("🔍 Movie Not Found");
        embed.setDescription("Sorry, I couldn't find any movie matching **" + movieTitle + "** on Letterboxd.\n\n" +
                "Try searching with a different title or check the spelling.");
        embed.setColor(Color.ORANGE);
        embed.setTimestamp(Instant.now());
        embed.setFooter("Powered by Letterboxd", null);
        return embed.build();
    }

    public MessageEmbed buildLoadingEmbed(String movieTitle) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("🎬 Searching...");
        embed.setDescription("Looking for **" + movieTitle + "** on Letterboxd...");
        embed.setColor(Color.YELLOW);
        embed.setTimestamp(Instant.now());
        return embed.build();
    }

    public MessageEmbed buildRatingsEmbed(MovieRatingsResult result, int page) {
        MovieData movie = result.getMovieData();
        List<Rating> ratings = result.getRatings();
        
        Map<String, Object> data = new HashMap<>();
        data.put("title", movie.getTitle());
        data.put("year", movie.getYear());
        data.put("url", movie.getUrl());
        data.put("posterUrl", movie.getPosterUrl());
        data.put("timestamp", Instant.now().toString());
        data.put("page", page);
        data.put("totalRatings", result.getTotalRatings());
        data.put("averageRating", String.format("%.1f", result.getAverageRating()));
        data.put("averageRatingWithStar", String.format("%.1f", result.getAverageRating()) + "★");

        if (ratings == null || ratings.isEmpty()) {
            data.put("noRatings", true);
        } else {
            data.put("noRatings", false);
            
            List<Map<String, String>> ratingList = new java.util.ArrayList<>();
            for (Rating rating : ratings) {
                Map<String, String> ratingData = new HashMap<>();
                ratingData.put("username", rating.getUsername());
                ratingData.put("rating", renderRating(rating.getRating()));
                ratingData.put("like", rating.isLiked() ? "<:dfds:1123838948611985418>" : "");
                ratingList.add(ratingData);
            }
            data.put("ratingsList", ratingList);
        }

        String json = templateUtil.renderTemplate("templates/movie-ratings-embed.json.mustache", data);
        log.info("Rendered ratings template JSON: {}", json);
        return buildEmbedFromJson(json);
    }

    private String renderRating(int rating) {
        if (rating == 0) {
            return "N/A";
        }
        return String.valueOf(rating);
    }
}
