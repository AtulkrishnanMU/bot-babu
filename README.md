# Discord Movie Bot

A feature-rich Discord bot that integrates with Letterboxd and TMDB APIs to provide comprehensive movie information, ratings, and images.

## Features

### Movie Information
- **Movie Search**: Search movies using Letterboxd integration
- **Detailed Movie Data**: Title, year, synopsis, cast, genres, runtime, language, country, studios
- **Poster Display**: High-quality movie posters with automatic size optimization

### Ratings System
- **User Ratings**: Fetch ratings from Letterboxd friends' lists
- **Paginated Display**: Browse through ratings with Previous/Next buttons
- **Rating Format**: Shows ratings out of 10 with liked status
- **Average Rating**: Calculated average with star indicator

### Image Gallery
- **Movie Posters**: Fetch all available posters from TMDB
- **Movie Backdrops**: Access high-quality backdrop images
- **Full Pagination**: Browse through complete image collections
- **Smart Sizing**: Automatically selects optimal image sizes

### Technical Features
- **Caching**: 24-hour cache for movies and ratings
- **Reactive Programming**: Non-blocking API calls with Reactor
- **Error Handling**: Graceful error handling with user-friendly messages
- **Slash Commands**: Modern Discord slash command interface

## Prerequisites

- Java 21
- Maven 3.8+
- Discord Bot Token ([Create one here](https://discord.com/developers/applications))
- TMDB API Key ([Get one here](https://www.themoviedb.org/settings/api))

## Configuration

Create `application.yml` in `src/main/resources/`:

```yaml
tmdb:
  api:
    key: "your_tmdb_api_key"

discord:
  token: "your_discord_bot_token"
  activity: "Searching movies 🎬"
  guild-id: "your_guild_id_optional"  # Leave empty for global commands

logging:
  level:
    com.example.discordbot: INFO
```

Or set environment variables:

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

## Installation

```bash
# Clone the repository
git clone <repository-url>
cd discord-bot

# Build the project
mvn clean install

# Run the application
mvn spring-boot:run
```

Or run the JAR directly:

```bash
java -jar target/discord-bot-1.0.0.jar
```

## Discord Commands

### `/movie [title]`
Search for a movie on Letterboxd and get detailed information.

**Example**: `/movie The Dark Knight`

**Returns**:
- Movie title, year, and poster
- Synopsis and runtime
- Cast and directors
- Genres and languages
- Production studios
- Release dates by country

### `/movie-ratings [title]`
Get user ratings for a movie from Letterboxd friends.

**Example**: `/movie-ratings Pulp Fiction`

**Features**:
- Shows individual ratings out of 10
- Displays liked status with ❤️
- Paginated results (10 ratings per page)
- Average rating with ⭐ indicator
- Previous/Next navigation buttons

### `/movie-poster [title]`
Fetch movie posters from TMDB.

**Example**: `/movie-poster Django Unchained`

**Features**:
- Displays one poster per page
- Full pagination through all available posters
- High-quality images (w500 size)
- Previous/Next navigation
- Shows actual movie title from TMDB

### `/movie-backdrop [title]`
Fetch movie backdrops from TMDB.

**Example**: `/movie-backdrop Inception`

**Features**:
- High-quality backdrop images (w1280 size)
- Full pagination support
- Perfect for banner-style images
- Same navigation as poster command

## Project Structure

```
src/main/java/com/example/discordbot/
├── config/
│   ├── DiscordConfig.java           # JDA setup and command registration
│   └── TmdbClientConfig.java        # TMDB WebClient configuration
├── client/
│   ├── dto/
│   │   ├── TmdbConfiguration.java   # TMDB image configuration
│   │   ├── TmdbMovieImages.java     # Movie images response
│   │   ├── TmdbMovieResult.java     # Search result item
│   │   └── TmdbSearchResponse.java  # Search response wrapper
│   └── TmdbApiClient.java           # TMDB API client interface
├── discord/
│   ├── embed/
│   │   └── MovieEmbedBuilder.java   # Discord embed formatting
│   └── listeners/
│       ├── MovieCommandListener.java        # Movie search command
│       ├── MovieRatingsCommandListener.java # Ratings command
│       └── MoviePosterCommandListener.java  # Poster/backdrop commands
├── dto/
│   ├── CachedMovieRecord.java       # Cache data structure
│   └── MovieRatingsResult.java      # Ratings pagination result
├── model/
│   ├── CastMember.java              # Cast member data
│   ├── MovieData.java               # Movie information
│   ├── Rating.java                  # User rating data
│   └── ReleaseDate.java             # Release date information
├── repository/
│   └── MovieRepository.java         # Database repository
├── service/
│   ├── LetterboxdSearchService.java # Letterboxd search
│   ├── LetterboxdScraperService.java # Web scraping service
│   └── TmdbPosterService.java       # TMDB image service
└── DiscordBotApplication.java       # Main application entry
```

## APIs Used

### Letterboxd
- Web scraping for movie details and ratings
- Friends list ratings extraction
- HTML parsing with Jsoup

### TMDB (The Movie Database)
- `GET /search/movie` - Movie search
- `GET /movie/{movie_id}` - Movie details
- `GET /movie/{movie_id}/images` - Movie posters and backdrops
- `GET /configuration` - Image configuration

## Caching Strategy

- **Movie Data**: 24-hour cache by film slug
- **Ratings**: 24-hour cache with all ratings pre-fetched
- **TMDB Configuration**: 24-hour global cache
- **Search Results**: Temporary cache for slug resolution

Benefits:
- Reduced API calls and faster response times
- Rate limit protection
- Improved user experience with instant responses

## Architecture

### Reactive Programming
- Uses Spring WebFlux and Project Reactor
- Non-blocking API calls
- `deferReply()` for responsive Discord interactions
- Error handling with `onErrorReturn()` and `onErrorResume()`

### Data Flow
1. User issues slash command
2. Command listener validates input
3. Service layer fetches data (cached or API)
4. Embed builder formats Discord response
5. Interactive components (buttons) for pagination

### Error Handling
- Graceful degradation for missing data
- User-friendly error messages
- Comprehensive logging
- Timeout handling for API calls

## Technologies

- **Spring Boot 3.2.0** - Application framework
- **Java 21** - Programming language
- **JDA 5.0.0-beta.20** - Discord API wrapper
- **Spring WebFlux** - Reactive HTTP client
- **Jsoup** - HTML parsing for Letterboxd
- **Caffeine Cache** - High-performance caching
- **Lombok** - Code generation
- **Gson** - JSON processing

## Development

### Adding New Commands
1. Create listener class extending `ListenerAdapter`
2. Register in `DiscordConfig.java`
3. Add command registration in `registerCommands()`
4. Create corresponding service methods

### Customizing Embeds
Modify `MovieEmbedBuilder.java` and Mustache templates in `src/main/resources/templates/`.

### Cache Configuration
Adjust cache settings in service classes using `@Cacheable` annotations.

## Deployment

### Docker (Optional)
```dockerfile
FROM openjdk:21-jre-slim
COPY target/discord-bot-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### Systemd Service
Create `/etc/systemd/system/discord-bot.service`:
```ini
[Unit]
Description=Discord Movie Bot
After=network.target

[Service]
Type=simple
User=discord-bot
WorkingDirectory=/opt/discord-bot
ExecStart=/usr/bin/java -jar discord-bot-1.0.0.jar
Restart=always

[Install]
WantedBy=multi-user.target
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

MIT License - see LICENSE file for details.

## Support

For issues and questions:
- Create an issue in the repository
- Check the logs for error details
- Verify API keys and configuration

## Changelog

### v1.0.0
- Initial release
- Movie search from Letterboxd
- Ratings fetching with pagination
- TMDB poster and backdrop gallery
- Comprehensive caching system
- Modern slash command interface