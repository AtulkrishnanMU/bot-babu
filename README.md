# Discord Bot with TMDB Integration

A Spring Boot 3.x microservice that functions as a Discord Bot backend, integrating with the TMDB (The Movie Database) API to fetch and display movie information.

## Features

- **Discord Integration**: Uses JDA (Java Discord API) to handle slash commands
- **TMDB Integration**: Uses Spring WebClient for asynchronous API calls
- **Caching**: Uses Spring's `@Cacheable` with Caffeine cache to store TMDB responses for 24 hours
- **Movie Search**: Returns movie details including title, release date, overview, rating, runtime, and poster

## Prerequisites

- Java 21
- Maven 3.8+
- Discord Bot Token ([Get one here](https://discord.com/developers/applications))
- TMDB API Key ([Get one here](https://www.themoviedb.org/settings/api))

## Configuration

Set the following environment variables:

```bash
# Windows
set TMDB_API_KEY=your_tmdb_api_key
set DISCORD_BOT_TOKEN=your_discord_bot_token
set DISCORD_GUILD_ID=your_guild_id_optional

# Linux/Mac
export TMDB_API_KEY=your_tmdb_api_key
export DISCORD_BOT_TOKEN=your_discord_bot_token
export DISCORD_GUILD_ID=your_guild_id_optional
```

Or create a `.env` file in the project root (not committed to git):

```
TMDB_API_KEY=your_tmdb_api_key
DISCORD_BOT_TOKEN=your_discord_bot_token
DISCORD_GUILD_ID=your_guild_id_optional
```

## Building the Project

```bash
mvn clean install
```

## Running the Application

```bash
mvn spring-boot:run
```

Or run the JAR directly:

```bash
java -jar target/discord-bot-1.0.0.jar
```

## Discord Commands

### /movie [title]

Searches for a movie by title and returns detailed information including:
- Title and tagline
- Genres
- Release date
- Runtime
- Rating with vote count
- Status
- Original language
- Budget and revenue
- Overview
- Poster thumbnail

Example: `/movie The Dark Knight`

## Project Structure

```
src/main/java/com/example/discordbot/
├── config/
│   ├── CacheConfig.java       # Spring Cache configuration
│   └── DiscordConfig.java     # JDA bean and command registration
├── discord/
│   ├── embed/
│   │   └── MovieEmbedBuilder.java   # Discord embed formatting utilities
│   └── listeners/
│       └── MovieCommandListener.java # Slash command event handler
├── client/
│   ├── dto/
│   │   ├── TmdbConfiguration.java   # TMDB config response
│   │   ├── TmdbMovieDetails.java    # Detailed movie info
│   │   ├── TmdbMovieResult.java     # Search result item
│   │   └── TmdbSearchResponse.java  # Search response wrapper
│   └── TmdbClient.java        # WebClient for TMDB API
├── service/
│   └── MovieService.java      # Business logic with caching
└── DiscordBotApplication.java # Main entry point
```

## TMDB APIs Used

- `GET /search/movie` - Search for movies by title
- `GET /movie/{movie_id}` - Get detailed movie information
- `GET /configuration` - Get base image URL for posters

## Caching Strategy

- **Search Results**: Cached by query string for 24 hours
- **Movie Details**: Cached by movie ID for 24 hours
- **Configuration**: Cached globally for 24 hours

This reduces API latency and helps stay within TMDB rate limits.

## Asynchronous Processing

The bot uses `deferReply()` to acknowledge slash commands immediately, preventing Discord interaction timeouts while waiting for TMDB API responses. This ensures a responsive user experience even when API calls take time.

## Technologies

- **Spring Boot 3.2.0** - Framework
- **Java 21** - Language
- **JDA 5.0.0-beta.20** - Discord API wrapper
- **Spring WebFlux** - Reactive HTTP client
- **Caffeine Cache** - High-performance caching
- **Lombok** - Boilerplate reduction

## License

MIT License
