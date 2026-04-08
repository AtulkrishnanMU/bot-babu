package com.example.discordbot.discord.listeners;

import com.example.discordbot.discord.embed.MovieEmbedBuilder;
import com.example.discordbot.model.Rating;
import com.example.discordbot.service.LetterboxdScraperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MovieRatingsCommandListener extends ListenerAdapter {

    private final LetterboxdScraperService letterboxdScraperService;
    private final MovieEmbedBuilder movieEmbedBuilder;

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("movie-ratings")) {
            return;
        }

        OptionMapping titleOption = event.getOption("title");
        if (titleOption == null) {
            event.replyEmbeds(movieEmbedBuilder.buildErrorEmbed(
                            "Invalid Command",
                            "Please provide a movie title to fetch ratings for."))
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

        log.info("Received /movie-ratings command from {} (guild: {}) for title: {}",
                event.getUser().getName(),
                event.getGuild() != null ? event.getGuild().getName() : "DM",
                movieTitle);

        event.deferReply().queue();

        letterboxdScraperService.fetchMovieRatings(movieTitle, 1)
                .subscribe(
                        result -> {
                            MessageEmbed embed = movieEmbedBuilder.buildRatingsEmbed(result, 1);
                            boolean hasNext = result.isHasNext();
                            
                            Button prevButton = Button.primary("rat_p_0_" + result.getSlug(), "Previous").asDisabled();
                            Button nextButton = Button.primary("rat_n_2_" + result.getSlug(), "Next").withDisabled(!hasNext);

                            event.getHook().sendMessageEmbeds(embed)
                                    .addActionRow(prevButton, nextButton)
                                    .queue(
                                            success -> log.info("Successfully sent ratings for: {}", movieTitle),
                                            error -> log.error("Failed to send ratings: {}", error.getMessage())
                                    );
                        },
                        error -> {
                            log.error("Error fetching ratings: {}", error.getMessage(), error);
                            event.getHook().sendMessageEmbeds(
                                    movieEmbedBuilder.buildErrorEmbed(
                                            "Error",
                                            "Failed to fetch ratings. Please try again later."
                                    )
                            ).queue();
                        }
                );
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        if (!componentId.startsWith("rat_n_") && !componentId.startsWith("rat_p_")) {
            return;
        }

        String[] parts = componentId.split("_", 4);
        if (parts.length < 4) return;
        
        int targetPage = Integer.parseInt(parts[2]);
        String slug = parts[3];

        event.deferEdit().queue();

        letterboxdScraperService.fetchMovieRatingsBySlug(slug, targetPage)
                .subscribe(
                        result -> {
                            MessageEmbed embed = movieEmbedBuilder.buildRatingsEmbed(result, targetPage);
                            boolean hasNext = result.isHasNext();
                            
                            Button prevButton = Button.primary("rat_p_" + (targetPage - 1) + "_" + slug, "Previous").withDisabled(targetPage <= 1);
                            Button nextButton = Button.primary("rat_n_" + (targetPage + 1) + "_" + slug, "Next").withDisabled(!hasNext);
                            
                            event.getHook().editOriginalEmbeds(embed)
                                 .setActionRow(prevButton, nextButton)
                                 .queue(
                                    success -> log.info("Successfully paginated ratings for: {} (Page {})", slug, targetPage),
                                    error -> log.error("Failed to paginate ratings: {}", error.getMessage())
                            );
                        },
                        error -> {
                            log.error("Error fetching paginated ratings for {}: {}", slug, error.getMessage(), error);
                            event.getHook().sendMessageEmbeds(
                                    movieEmbedBuilder.buildErrorEmbed(
                                            "Error",
                                            "Failed to fetch ratings page. Please try again later."
                                    )
                            ).setEphemeral(true).queue();
                        }
                );
    }
}
