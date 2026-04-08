package com.example.discordbot.discord.listeners;

import com.example.discordbot.discord.embed.MovieEmbedBuilder;
import com.example.discordbot.service.TmdbPosterService;
import com.example.discordbot.service.TmdbPosterService.MovieImagesResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MoviePosterCommandListener extends ListenerAdapter {

    private final TmdbPosterService tmdbPosterService;
    private final MovieEmbedBuilder movieEmbedBuilder;

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String commandName = event.getName();
        
        if (!commandName.equals("movie-poster") && !commandName.equals("movie-backdrop")) {
            return;
        }

        OptionMapping titleOption = event.getOption("title");
        if (titleOption == null) {
            event.replyEmbeds(movieEmbedBuilder.buildErrorEmbed(
                            "Invalid Command",
                            "Please provide a movie title to fetch images for."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        String movieTitle = titleOption.getAsString().trim();
        if (movieTitle.isEmpty()) {
            event.replyEmbeds(movieEmbedBuilder.buildErrorEmbed(
                            "Invalid Input",
                            "Movie title cannot be empty."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        log.info("Received /{} command from {} (guild: {}) for title: {}",
                commandName,
                event.getUser().getName(),
                event.getGuild() != null ? event.getGuild().getName() : "DM",
                movieTitle);

        event.deferReply().queue();

        boolean isPoster = commandName.equals("movie-poster");
        
        if (isPoster) {
            tmdbPosterService.getMoviePosters(movieTitle)
                    .subscribe(
                            result -> handleImageResponse(event, result, "poster", 1),
                            error -> handleError(event, movieTitle, error)
                    );
        } else {
            tmdbPosterService.getMovieBackdrops(movieTitle)
                    .subscribe(
                            result -> handleImageResponse(event, result, "backdrop", 1),
                            error -> handleError(event, movieTitle, error)
                    );
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        if (!componentId.startsWith("poster_") && !componentId.startsWith("backdrop_")) {
            return;
        }

        String[] parts = componentId.split("_", 3);
        if (parts.length < 3) return;
        
        String imageType = parts[0];
        int targetPage = Integer.parseInt(parts[1]);
        String movieTitle = parts[2].replace("-", " ");

        event.deferEdit().queue();

        if (imageType.equals("poster")) {
            tmdbPosterService.getMoviePosters(movieTitle)
                    .subscribe(
                            result -> handleImagePagination(event, result, "poster", targetPage),
                            error -> handlePaginationError(event, movieTitle, error)
                    );
        } else {
            tmdbPosterService.getMovieBackdrops(movieTitle)
                    .subscribe(
                            result -> handleImagePagination(event, result, "backdrop", targetPage),
                            error -> handlePaginationError(event, movieTitle, error)
                    );
        }
    }

    private void handleImageResponse(SlashCommandInteractionEvent event, MovieImagesResult result, 
                                   String imageType, int page) {
        List<String> images = result.getImageUrls();
        String movieTitle = result.getMovieTitle();
        
        if (images.isEmpty()) {
            event.getHook().sendMessageEmbeds(
                    movieEmbedBuilder.buildErrorEmbed(
                            "No Images Found",
                            String.format("No %s found for movie: **%s**", imageType, movieTitle)
                    )
            ).queue();
            return;
        }

        MessageEmbed embed = buildImageEmbed(movieTitle, images, imageType, page);
        boolean hasNext = page * 1 < images.size(); // 1 image per page
        boolean hasPrev = page > 1;
        
        String movieTitleForButton = movieTitle.replace(" ", "-");
        
        Button prevButton = Button.primary(
            imageType + "_" + (page - 1) + "_" + movieTitleForButton, 
            "Previous"
        ).withDisabled(!hasPrev);
        
        Button nextButton = Button.primary(
            imageType + "_" + (page + 1) + "_" + movieTitleForButton, 
            "Next"
        ).withDisabled(!hasNext);

        event.getHook().sendMessageEmbeds(embed)
                .addActionRow(prevButton, nextButton)
                .queue(
                        success -> log.info("Successfully sent {} images for: {}", imageType, movieTitle),
                        error -> log.error("Failed to send {} images: {}", imageType, error.getMessage())
                );
    }

    private void handleImagePagination(ButtonInteractionEvent event, MovieImagesResult result, 
                                     String imageType, int page) {
        List<String> images = result.getImageUrls();
        String movieTitle = result.getMovieTitle();
        
        MessageEmbed embed = buildImageEmbed(movieTitle, images, imageType, page);
        boolean hasNext = page * 1 < images.size(); // 1 image per page
        boolean hasPrev = page > 1;
        
        String movieTitleForButton = movieTitle.replace(" ", "-");
        
        Button prevButton = Button.primary(
            imageType + "_" + (page - 1) + "_" + movieTitleForButton, 
            "Previous"
        ).withDisabled(!hasPrev);
        
        Button nextButton = Button.primary(
            imageType + "_" + (page + 1) + "_" + movieTitleForButton, 
            "Next"
        ).withDisabled(!hasNext);
        
        event.getHook().editOriginalEmbeds(embed)
                .setActionRow(prevButton, nextButton)
                .queue(
                        success -> log.info("Successfully paginated {} for: {} (Page {})", imageType, movieTitle, page),
                        error -> log.error("Failed to paginate {}: {}", imageType, error.getMessage())
                );
    }

    private MessageEmbed buildImageEmbed(String movieTitle, List<String> images, String imageType, int page) {
        int imageIndex = (page - 1);
        String currentImage = images.get(imageIndex);
        
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(String.format("%s - %s (%d/%d)", movieTitle, 
                imageType.substring(0, 1).toUpperCase() + imageType.substring(1),
                page, images.size()));
        embed.setColor(Color.BLUE);
        embed.setImage(currentImage);
        
        StringBuilder description = new StringBuilder();
        description.append(String.format("**Found %d %ss**\n\n", images.size(), imageType));
        description.append(String.format("Image %d of %d", page, images.size()));
        
        embed.setDescription(description.toString());
        embed.setFooter("Source: TMDB", null);

        return embed.build();
    }

    private void handleError(SlashCommandInteractionEvent event, String movieTitle, Throwable error) {
        log.error("Error fetching images for {}: {}", movieTitle, error.getMessage(), error);
        event.getHook().sendMessageEmbeds(
                movieEmbedBuilder.buildErrorEmbed(
                        "Error",
                        "Failed to fetch images. The movie might not be found on TMDB. Please try again later."
                )
        ).queue();
    }

    private void handlePaginationError(ButtonInteractionEvent event, String movieTitle, Throwable error) {
        log.error("Error fetching paginated images for {}: {}", movieTitle, error.getMessage(), error);
        event.getHook().sendMessageEmbeds(
                movieEmbedBuilder.buildErrorEmbed(
                        "Error",
                        "Failed to fetch images page. Please try again later."
                )
        ).setEphemeral(true).queue();
    }
}
