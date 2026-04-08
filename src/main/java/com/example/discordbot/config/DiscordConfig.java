package com.example.discordbot.config;

import com.example.discordbot.discord.listeners.MovieCommandListener;
import com.example.discordbot.discord.listeners.MoviePosterCommandListener;
import com.example.discordbot.discord.listeners.MovieRatingsCommandListener;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class DiscordConfig {

    @Value("${discord.token}")
    private String discordToken;

    @Value("${discord.activity:Searching movies 🎬}")
    private String discordActivity;

    @Value("${discord.guild-id:}")
    private String guildId;

    @Bean
    public JDA jda(MovieCommandListener movieCommandListener, 
                   MovieRatingsCommandListener movieRatingsCommandListener,
                   MoviePosterCommandListener moviePosterCommandListener) throws Exception {
        log.info("Initializing Discord JDA client...");
        
        JDA jda = JDABuilder.createDefault(discordToken)
                .enableIntents(GatewayIntent.GUILD_MESSAGES)
                .addEventListeners(movieCommandListener, movieRatingsCommandListener, moviePosterCommandListener)
                .setActivity(Activity.watching(discordActivity))
                .build();
        
        jda.awaitReady();
        log.info("Discord JDA client initialized successfully");
        
        registerCommands(jda);
        
        return jda;
    }

    private void registerCommands(JDA jda) {
        log.info("Registering slash commands...");
        
        var movieCommand = Commands.slash("movie", "Search for a movie on Letterboxd")
                .addOption(OptionType.STRING, "title", "The title of the movie to search for", true);
        
        var movieRatingsCommand = Commands.slash("movie-ratings", "Get user ratings for a movie")
                .addOption(OptionType.STRING, "title", "The title of the movie to fetch ratings for", true);
        
        var moviePosterCommand = Commands.slash("movie-poster", "Get movie posters from TMDB")
                .addOption(OptionType.STRING, "title", "The title of the movie to fetch posters for", true);
        
        var movieBackdropCommand = Commands.slash("movie-backdrop", "Get movie backdrops from TMDB")
                .addOption(OptionType.STRING, "title", "The title of the movie to fetch backdrops for", true);
        
        if (guildId != null && !guildId.isEmpty()) {
            var guild = jda.getGuildById(guildId);
            if (guild != null) {
                guild.updateCommands()
                        .addCommands(movieCommand, movieRatingsCommand, moviePosterCommand, movieBackdropCommand)
                        .queue(
                                success -> log.info("Slash commands registered for guild: {}", guildId),
                                error -> log.error("Failed to register slash commands: {}", error.getMessage())
                        );
            } else {
                log.warn("Guild with ID {} not found, registering globally", guildId);
                registerGlobalCommands(jda, movieCommand, movieRatingsCommand, moviePosterCommand, movieBackdropCommand);
            }
        } else {
            registerGlobalCommands(jda, movieCommand, movieRatingsCommand, moviePosterCommand, movieBackdropCommand);
        }
    }

    private void registerGlobalCommands(JDA jda, net.dv8tion.jda.api.interactions.commands.build.CommandData... commands) {
        jda.updateCommands()
                .addCommands(commands)
                .queue(
                        success -> log.info("Slash commands registered globally"),
                        error -> log.error("Failed to register global slash commands: {}", error.getMessage())
                );
    }
}
