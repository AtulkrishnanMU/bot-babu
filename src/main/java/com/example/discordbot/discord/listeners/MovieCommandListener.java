package com.example.discordbot.discord.listeners;

import com.example.discordbot.discord.embed.MovieEmbedBuilder;
import com.example.discordbot.service.LetterboxdScraperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@RequiredArgsConstructor
public class MovieCommandListener extends ListenerAdapter {

    private final LetterboxdScraperService letterboxdScraperService;
    private final MovieEmbedBuilder movieEmbedBuilder;

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("movie")) {
            return;
        }

        OptionMapping titleOption = event.getOption("title");
        if (titleOption == null) {
            event.replyEmbeds(movieEmbedBuilder.buildErrorEmbed(
                    "Invalid Command",
                    "Please provide a movie title to search for."))
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

        log.info("Received /movie command from {} (guild: {}) for title: {}", 
                event.getUser().getName(), 
                event.getGuild() != null ? event.getGuild().getName() : "DM",
                movieTitle);

        event.deferReply().queue();

        letterboxdScraperService.searchAndScrapeMovie(movieTitle)
                .subscribe(movie -> {
                    if (movie != null) {
                        event.getHook().sendMessageEmbeds(
                                movieEmbedBuilder.buildDetailedMovieEmbed(movie)
                        ).queue(
                                success -> log.info("Successfully sent movie details for: {}", movie.getTitle()),
                                error -> log.error("Failed to send movie details: {}", error.getMessage())
                        );
                    } else {
                        event.getHook().sendMessageEmbeds(
                                movieEmbedBuilder.buildNotFoundEmbed(movieTitle)
                        ).queue(
                                success -> log.info("Movie not found: {}", movieTitle),
                                error -> log.error("Failed to send not found message: {}", error.getMessage())
                        );
                    }
                }, error -> {
                    log.error("Error processing movie command: {}", error.getMessage(), error);
                    event.getHook().sendMessageEmbeds(
                            movieEmbedBuilder.buildErrorEmbed(
                                    "Error",
                                    "An unexpected error occurred while searching for the movie. Please try again later."
                            )
                    ).queue();
                });
    }
}
