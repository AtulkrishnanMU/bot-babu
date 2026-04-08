package com.example.discordbot.service;

import com.example.discordbot.dto.CachedMovieRecord;
import com.example.discordbot.dto.MovieRatingsResult;
import com.example.discordbot.model.*;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.example.discordbot.repository.MovieRepository;
import com.example.discordbot.service.LetterboxdSearchService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import org.springframework.scheduling.annotation.Scheduled;
import reactor.core.publisher.Flux;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class LetterboxdScraperService {

    private final LetterboxdSearchService letterboxdSearchService;
    private final MovieRepository movieRepository;
    private final Gson gson;

    private final Cache<String, CachedMovieRecord> movieCache = Caffeine.newBuilder()
            .expireAfterWrite(24, TimeUnit.HOURS)
            .build();

    private final Cache<String, String> titleToSlugCache = Caffeine.newBuilder()
            .expireAfterWrite(24, TimeUnit.HOURS)
            .build();

    public LetterboxdScraperService(LetterboxdSearchService letterboxdSearchService, MovieRepository movieRepository) {
        this.letterboxdSearchService = letterboxdSearchService;
        this.movieRepository = movieRepository;
        this.gson = new Gson();
    }

    public Mono<MovieData> searchAndScrapeMovie(String movieTitle) {
        String normalizedTitle = movieTitle.toLowerCase().trim();
        String cachedSlug = titleToSlugCache.getIfPresent(normalizedTitle);

        if (cachedSlug != null) {
            CachedMovieRecord record = movieCache.getIfPresent(cachedSlug);
            if (record != null && record.getMovieData() != null) {
                log.info("Cache hit for MovieData (via title query): {}", movieTitle);
                return Mono.just(record.getMovieData());
            }
        }

        log.info("Title cache miss for: {}. Searching Letterboxd...", movieTitle);
        return letterboxdSearchService.searchLetterboxdLink(movieTitle)
                .flatMap(url -> {
                    String filmSlug = extractFilmSlug(url);
                    titleToSlugCache.put(normalizedTitle, filmSlug);

                    CachedMovieRecord record = movieCache.getIfPresent(filmSlug);
                    if (record != null && record.getMovieData() != null) {
                        log.info("Cache hit for MovieData (via resolved URL): {}", filmSlug);
                        return Mono.just(record.getMovieData());
                    }

                    log.info("Cache miss for MovieData. Scraping web for: {}", filmSlug);
                    return scrapeMovieFromUrl(url)
                            .doOnNext(movieData -> {
                                CachedMovieRecord existing = movieCache.getIfPresent(filmSlug);
                                if (existing != null) {
                                    movieCache.put(filmSlug, new CachedMovieRecord(movieData, existing.getAllRatings()));
                                } else {
                                    movieCache.put(filmSlug, new CachedMovieRecord(movieData, null));
                                }
                                saveToDatabase(movieData);
                            });
                });
    }

    public Mono<MovieData> scrapeMovieFromUrl(String url) {
        return Mono.fromCallable(() -> {
                    log.info("Scraping Letterboxd movie from: {}", url);

                    Document doc = Jsoup.connect(url)
                            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                            .timeout(30000)
                            .get();

                    // First try to parse JSON-LD structured data (most reliable)
                    MovieData movie = parseJsonLd(doc);

                    if (movie == null) {
                        log.warn("Failed to parse JSON-LD, falling back to HTML parsing");
                        movie = new MovieData();
                    }

                    // Fill in any missing data from HTML
                    fillFromHtml(doc, movie);

                    movie.setUrl(url);

                    log.info("Successfully scraped movie: {} ({})", movie.getTitle(), movie.getYear());
                    return movie;

                }).subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> log.error("Failed to scrape movie from {}: {}", url, e.getMessage(), e));
    }

    private MovieData parseJsonLd(Document doc) {
        try {
            Elements jsonLdScripts = doc.select("script[type=application/ld+json]");

            for (Element script : jsonLdScripts) {
                String json = script.html();
                // Remove CDATA wrapper if present
                json = json.replace("/* <![CDATA[ */", "")
                        .replace("/* ]]> */", "")
                        .trim();

                JsonObject jsonObj = gson.fromJson(json, JsonObject.class);

                if (jsonObj != null && jsonObj.has("@type") && "Movie".equals(jsonObj.get("@type").getAsString())) {
                    MovieData movie = new MovieData();

                    // Title
                    if (jsonObj.has("name")) {
                        movie.setTitle(jsonObj.get("name").getAsString());
                    }

                    // Year from releasedEvent
                    if (jsonObj.has("releasedEvent")) {
                        JsonArray events = jsonObj.getAsJsonArray("releasedEvent");
                        if (events != null && events.size() > 0) {
                            JsonObject event = events.get(0).getAsJsonObject();
                            if (event.has("startDate")) {
                                movie.setYear(event.get("startDate").getAsString());
                            }
                        }
                    }

                    // Poster
                    if (jsonObj.has("image")) {
                        String imageUrl = jsonObj.get("image").getAsString();
                        // Replace small with larger image
                        imageUrl = imageUrl.replace("-0-230-0-345-crop", "-0-1000-0-1500-crop");
                        movie.setPosterUrl(imageUrl);
                        log.info("Found poster in JSON-LD: {}", imageUrl);
                    }

                    // Directors
                    if (jsonObj.has("director")) {
                        JsonArray directors = jsonObj.getAsJsonArray("director");
                        List<String> directorNames = new ArrayList<>();
                        for (JsonElement d : directors) {
                            JsonObject dir = d.getAsJsonObject();
                            if (dir.has("name")) {
                                directorNames.add(dir.get("name").getAsString());
                            }
                        }
                        if (!directorNames.isEmpty()) {
                            Map<String, List<String>> crew = new HashMap<>();
                            crew.put("Director", directorNames);
                            movie.setCrew(crew);
                        }
                    }

                    // Actors/Cast
                    if (jsonObj.has("actors")) {
                        JsonArray actors = jsonObj.getAsJsonArray("actors");
                        List<CastMember> cast = new ArrayList<>();
                        for (JsonElement a : actors) {
                            JsonObject actor = a.getAsJsonObject();
                            if (actor.has("name")) {
                                CastMember member = new CastMember();
                                member.setActor(actor.get("name").getAsString());
                                cast.add(member);
                            }
                        }
                        movie.setCast(cast);
                    }

                    // Genres
                    if (jsonObj.has("genre")) {
                        JsonElement genreElem = jsonObj.get("genre");
                        List<String> genres = new ArrayList<>();
                        if (genreElem.isJsonArray()) {
                            for (JsonElement g : genreElem.getAsJsonArray()) {
                                genres.add(g.getAsString());
                            }
                        } else if (genreElem.isJsonPrimitive()) {
                            genres.add(genreElem.getAsString());
                        }
                        movie.setGenres(genres);
                    }

                    // Studios (productionCompany)
                    if (jsonObj.has("productionCompany")) {
                        JsonArray companies = jsonObj.getAsJsonArray("productionCompany");
                        List<String> studios = new ArrayList<>();
                        for (JsonElement c : companies) {
                            JsonObject company = c.getAsJsonObject();
                            if (company.has("name")) {
                                studios.add(company.get("name").getAsString());
                            }
                        }
                        movie.setStudios(studios);
                    }

                    // Rating from aggregateRating
                    if (jsonObj.has("aggregateRating")) {
                        JsonObject rating = jsonObj.getAsJsonObject("aggregateRating");
                        if (rating.has("ratingValue")) {
                            movie.setRating(rating.get("ratingValue").getAsDouble());
                        }
                        if (rating.has("ratingCount")) {
                            movie.setRatingCount(rating.get("ratingCount").getAsInt());
                        }
                    }

                    // Country from countryOfOrigin
                    if (jsonObj.has("countryOfOrigin")) {
                        JsonArray countries = jsonObj.getAsJsonArray("countryOfOrigin");
                        List<String> countryNames = new ArrayList<>();
                        for (JsonElement c : countries) {
                            JsonObject country = c.getAsJsonObject();
                            if (country.has("name")) {
                                countryNames.add(country.get("name").getAsString());
                            }
                        }
                        movie.setCountries(countryNames);
                    }

                    log.info("Successfully parsed JSON-LD for movie: {}", movie.getTitle());
                    return movie;
                }
            }
        } catch (Exception e) {
            log.error("Error parsing JSON-LD: {}", e.getMessage(), e);
        }

        return null;
    }

    /**
     * Fill in missing data from HTML using MovieUtil techniques
     */
    private void fillFromHtml(Document doc, MovieData movie) {
        // Title from meta tag (MovieUtil approach)
        if (movie.getTitle() == null) {
            Element titleMeta = doc.selectFirst("meta[property=og:title]");
            if (titleMeta != null) {
                String title = titleMeta.attr("content");
                // Extract year from title if present (e.g., "Haider (2014)")
                if (title.contains("(")) {
                    int yearStart = title.lastIndexOf("(");
                    int yearEnd = title.lastIndexOf(")");
                    if (yearEnd > yearStart) {
                        movie.setYear(title.substring(yearStart + 1, yearEnd));
                        title = title.substring(0, yearStart).trim();
                    }
                }
                movie.setTitle(title);
            }
        }

        // Synopsis from description meta
        if (movie.getSynopsis() == null) {
            Element descMeta = doc.selectFirst("meta[name=description]");
            if (descMeta != null) {
                movie.setSynopsis(descMeta.attr("content"));
            }
        }

        // Director from twitter data (MovieUtil approach)
        if (movie.getCrew() == null || movie.getCrew().get("Director") == null) {
            Element directorMeta = doc.selectFirst("meta[name=twitter:data1]");
            if (directorMeta != null) {
                String director = directorMeta.attr("content");
                Map<String, List<String>> crew = movie.getCrew() != null ? movie.getCrew() : new HashMap<>();
                List<String> directors = new ArrayList<>();
                directors.add(director);
                crew.put("Director", directors);
                movie.setCrew(crew);
            }
        }

        // Rating from twitter data (MovieUtil approach) - "4.06 out of 5"
        if (movie.getRating() == null || movie.getRating() == 0) {
            Element ratingMeta = doc.selectFirst("meta[name=twitter:data2]");
            if (ratingMeta != null) {
                String ratingText = ratingMeta.attr("content");
                try {
                    // Parse "4.06 out of 5" format
                    double rating = Double.parseDouble(ratingText.split(" ")[0]) * 2; // Multiply by 2 for 10-point scale
                    movie.setRating(rating);
                } catch (Exception e) {
                    log.warn("Could not parse rating: {}", ratingText);
                }
            }
        }

        // Duration from text-link (MovieUtil approach)
        if (movie.getRuntime() == null || movie.getRuntime().isEmpty()) {
            Element durationElement = doc.selectFirst("p.text-link.text-footer");
            if (durationElement != null) {
                String text = durationElement.text();
                // Extract first number (minutes)
                String[] parts = text.split("\\s+");
                if (parts.length > 0) {
                    try {
                        int minutes = Integer.parseInt(parts[0]);
                        movie.setRuntime(minutes + " mins");
                    } catch (NumberFormatException e) {
                        movie.setRuntime(text);
                    }
                } else {
                    movie.setRuntime(text);
                }
            }
        }

        // Genres from text-sluglist (MovieUtil approach)
        if (movie.getGenres() == null || movie.getGenres().isEmpty()) {
            Elements genreLinks = doc.select("#tab-genres h3:containsOwn(Genre) + .text-sluglist p a.text-slug");
            if (!genreLinks.isEmpty()) {
                List<String> genres = new ArrayList<>();
                for (Element genre : genreLinks) {
                    genres.add(genre.text());
                }
                movie.setGenres(genres);
            }
        }

        // Languages from text-sluglist (MovieUtil approach)
        if (movie.getLanguages() == null || movie.getLanguages().isEmpty()) {
            Elements langLinks = doc.select("h3:containsOwn(Language) + .text-sluglist p a.text-slug");
            if (!langLinks.isEmpty()) {
                List<String> languages = new ArrayList<>();
                for (Element lang : langLinks) {
                    languages.add(lang.text());
                }
                movie.setLanguages(languages);
            }
        }

        // Countries from text-sluglist (MovieUtil approach) - includes "Countr" for Country/Countries
        if (movie.getCountries() == null || movie.getCountries().isEmpty()) {
            Elements countryLinks = doc.select("h3:containsOwn(Countr) + .text-sluglist p a.text-slug");
            if (!countryLinks.isEmpty()) {
                List<String> countries = new ArrayList<>();
                for (Element country : countryLinks) {
                    countries.add(country.text());
                }
                movie.setCountries(countries);
            }
        }

        // Release dates from release table (MovieUtil approach)
        Elements dateElements = doc.select(".release-table .listitem .cell h5.date");
        if (!dateElements.isEmpty()) {
            List<ReleaseDate> releaseDates = new ArrayList<>();
            for (Element dateEl : dateElements) {
                ReleaseDate rd = new ReleaseDate();
                rd.setDate(dateEl.text());
                // Try to find country/type in nearby elements
                Element parent = dateEl.parent();
                if (parent != null) {
                    Element typeEl = parent.selectFirst(".type");
                    if (typeEl != null) {
                        rd.setType(typeEl.text());
                    }
                    Element countryEl = parent.selectFirst(".country");
                    if (countryEl != null) {
                        rd.setCountry(countryEl.text());
                    }
                }
                releaseDates.add(rd);
            }
            movie.setReleaseDates(releaseDates);
        }

        // Cast from cast section
        if (movie.getCast() == null || movie.getCast().isEmpty()) {
            Elements castElements = doc.select("#tab-cast .cast-list .cast-member");
            if (!castElements.isEmpty()) {
                List<CastMember> cast = new ArrayList<>();
                for (Element castEl : castElements) {
                    CastMember member = new CastMember();

                    Element actorEl = castEl.selectFirst("a[href*='/actor/']");
                    if (actorEl != null) {
                        member.setActor(actorEl.text());
                    }

                    Element characterEl = castEl.selectFirst(".character-name");
                    if (characterEl != null) {
                        member.setCharacter(characterEl.text());
                    }

                    if (member.getActor() != null) {
                        cast.add(member);
                    }
                }
                if (!cast.isEmpty()) {
                    movie.setCast(cast);
                }
            }
        }

        // Studios from text-sluglist
        if (movie.getStudios() == null || movie.getStudios().isEmpty()) {
            Elements studioLinks = doc.select("h3:containsOwn(Studio) + .text-sluglist p a.text-slug");
            if (!studioLinks.isEmpty()) {
                List<String> studios = new ArrayList<>();
                for (Element studio : studioLinks) {
                    studios.add(studio.text());
                }
                movie.setStudios(studios);
            }
        }

        // Poster fallback from HTML if not found in JSON-LD
        if (movie.getPosterUrl() == null || movie.getPosterUrl().isEmpty()) {
            Element posterImg = doc.selectFirst(".poster img, img[src*='resized'], img[src*='poster']");
            if (posterImg != null) {
                String posterUrl = posterImg.attr("src");
                // Replace small with larger image if needed
                if (posterUrl.contains("-0-230-0-345-crop")) {
                    posterUrl = posterUrl.replace("-0-230-0-345-crop", "-0-1000-0-1500-crop");
                }
                movie.setPosterUrl(posterUrl);
                log.info("Found poster from HTML fallback: {}", posterUrl);
            }
        }
    }

    /**
     * Fetch user ratings for a movie
     */
    public Mono<MovieRatingsResult> fetchMovieRatings(String movieTitle, int page) {
        return searchAndScrapeMovie(movieTitle)
                .flatMap(movieData -> {
                    // Extract film slug from URL
                    String filmSlug = extractFilmSlug(movieData.getUrl());
                    return getCachedOrFetchAllRatings(filmSlug, movieData)
                            .map(allRatings -> mapToPaginatedResult(filmSlug, movieData, allRatings, page));
                });
    }

    public Mono<MovieRatingsResult> fetchMovieRatingsBySlug(String filmSlug, int page) {
        CachedMovieRecord record = movieCache.getIfPresent(filmSlug);
        if (record != null && record.getMovieData() != null && record.getAllRatings() != null) {
            log.info("Memory cache hit for: {}", filmSlug);
            return Mono.just(mapToPaginatedResult(filmSlug, record.getMovieData(), record.getAllRatings(), page));
        }

        log.info("Memory cache miss for: {}", filmSlug);
        String movieUrl = "https://letterboxd.com/film/" + filmSlug + "/";
        return scrapeMovieFromUrl(movieUrl)
                .flatMap(movieData -> getCachedOrFetchAllRatings(filmSlug, movieData)
                        .map(allRatings -> mapToPaginatedResult(filmSlug, movieData, allRatings, page))
                );
    }

    private Mono<List<Rating>> getCachedOrFetchAllRatings(String filmSlug, MovieData movieData) {
        CachedMovieRecord cached = movieCache.getIfPresent(filmSlug);
        if (cached != null && cached.getAllRatings() != null) {
            log.info("Cache hit for Ratings: {}", filmSlug);
            return Mono.just(cached.getAllRatings());
        }

        log.info("Cache miss for Ratings. Scraping web for: {}", filmSlug);
        return fetchRatingsPageRecursively(filmSlug, 1, new ArrayList<>())
                .map(ratings -> {
                    ratings.sort((r1, r2) -> Integer.compare(r2.getRating(), r1.getRating()));
                    movieCache.put(filmSlug, new CachedMovieRecord(movieData, ratings));
                    log.info("Saved new ratings to cache for: {}", filmSlug);
                    return ratings;
                });
    }

    @Scheduled(fixedRate = 24L * 60 * 60 * 1000)
    public void refreshCachedMovies() {
        Set<String> activeSlugs = new HashSet<>(movieCache.asMap().keySet());
        if (activeSlugs.isEmpty()) {
            log.info("No cached movies to refresh.");
            return;
        }

        log.info("Starting scheduled refresh of {} cached movies", activeSlugs.size());

        Flux.fromIterable(activeSlugs)
                .delayElements(Duration.ofSeconds(5))
                .flatMap(slug -> {
                    log.info("Refreshing cache for movie: {}", slug);
                    String movieUrl = "https://letterboxd.com/film/" + slug + "/";
                    return scrapeMovieFromUrl(movieUrl)
                            .flatMap(movieData -> fetchRatingsPageRecursively(slug, 1, new ArrayList<>())
                                    .map(ratings -> {
                                        ratings.sort((r1, r2) -> Integer.compare(r2.getRating(), r1.getRating()));
                                        movieCache.put(slug, new CachedMovieRecord(movieData, ratings));
                                        log.info("Successfully refreshed cache for movie: {}", slug);
                                        return slug;
                                    })
                            )
                            .onErrorResume(e -> {
                                log.error("Failed to refresh cache for {}: {}", slug, e.getMessage());
                                return Mono.empty();
                            });
                })
                .doOnComplete(() -> log.info("Finished scheduled cache refresh for {} movies", activeSlugs.size()))
                .subscribe();
    }

    private Mono<List<Rating>> fetchRatingsPageRecursively(String filmSlug, int page, List<Rating> accumulated) {
        String pageSuffix = page > 1 ? "page/" + page + "/" : "";
        String ratingsUrl = "https://letterboxd.com/travis_pickle12/friends/film/" + filmSlug + "/" + pageSuffix;

        return fetchRatingsFromUrl(ratingsUrl)
                .flatMap(ratings -> {
                    accumulated.addAll(ratings);
                    if (ratings.isEmpty() || ratings.size() < 10) {
                        return Mono.just(accumulated);
                    } else {
                        return fetchRatingsPageRecursively(filmSlug, page + 1, accumulated);
                    }
                });
    }

    private MovieRatingsResult mapToPaginatedResult(String slug, MovieData movieData, List<Rating> allRatings, int page) {
        int pageSize = 10;
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, allRatings.size());

        List<Rating> pageRatings = (start < allRatings.size() && start >= 0) ? allRatings.subList(start, end) : new ArrayList<>();

        long ratedCount = allRatings.stream()
                .filter(r -> r.getRating() > 0)
                .count();

        double avg = allRatings.stream()
                .filter(r -> r.getRating() > 0)
                .mapToInt(Rating::getRating)
                .average()
                .orElse(0.0);

        boolean hasNext = end < allRatings.size();

        return new MovieRatingsResult(slug, movieData, pageRatings, (int) ratedCount, avg, hasNext);
    }

    /**
     * Extract film slug from Letterboxd URL
     * Example: https://letterboxd.com/film/haider/ -> haider
     */
    private String extractFilmSlug(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }

        // Pattern: /film/{slug}/
        Pattern pattern = Pattern.compile("/film/([^/]+)/");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return "";
    }

    /**
     * Fetch ratings from a ratings page URL (MovieUtil approach)
     */
    public Mono<List<Rating>> fetchRatingsFromUrl(String ratingsUrl) {
        return Mono.fromCallable(() -> {
                    log.info("Fetching ratings from: {}", ratingsUrl);

                    Document doc = Jsoup.connect(ratingsUrl)
                            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                            .referrer("https://letterboxd.com")
                            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                            .header("Accept-Language", "en-US,en;q=0.5")
                            .header("Accept-Encoding", "gzip, deflate")
                            .header("Connection", "keep-alive")
                            .timeout(30000)
                            .get();

                    List<Rating> ratings = new ArrayList<>();
                    Elements rows = doc.select("table.reactions-table tr");

                    for (Element row : rows) {
                        // Get name from the name link
                        Element nameEl = row.selectFirst("td.col-member .name");
                        if (nameEl == null) continue;

                        String name = nameEl.text().trim();

                        // Get rating stars from the rating span
                        Element ratingEl = row.selectFirst("td.col-rating .rating");
                        String ratingStars = ratingEl != null ? ratingEl.text() : "";

                        // Check for liked status
                        boolean liked = row.selectFirst("td.col-like .icon-liked") != null;

                        if (name != null && !name.isEmpty()) {
                            int rating = parseRatingStars(ratingStars);

                            // Shorten long names
                            if (name.length() > 19) {
                                name = name.replace(" (aka Pardesi)", "") + "...";
                            }

                            Rating r = new Rating();
                            r.setUsername(name);
                            r.setRating(rating);
                            r.setLiked(liked);
                            ratings.add(r);

                            log.debug("Added rating: {} - {} stars, liked: {}", name, rating, liked);
                        }
                    }

                    log.info("Fetched {} ratings from {}", ratings.size(), ratingsUrl);
                    return ratings;

                }).subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> log.error("Failed to fetch ratings from {}: {}", ratingsUrl, e.getMessage(), e));
    }

    /**
     * Clean text from element (MovieUtil approach)
     */
    private String cleanText(Element element) {
        if (element == null) {
            return "";
        }
        return element.text().replaceAll("\\<.*?\\>", "").trim();
    }

    /**
     * Parse rating stars to numeric value (MovieUtil approach)
     * Handles patterns like "★★★★½" or "★★★★★"
     */
    private int parseRatingStars(String ratingStars) {
        if (ratingStars == null || ratingStars.isEmpty()) {
            return 0;
        }

        int rating = 0;
        for (char c : ratingStars.toCharArray()) {
            if (c == '\u2605') { // ★ star
                rating += 2;
            } else if (c == '\u00BD') { // ½ half star
                rating += 1;
            }
        }
        return rating; // Scale of 10
    }

    /**
     * Save movie to database
     */
    private void saveToDatabase(MovieData movie) {
        try {
            // Check if movie already exists
            Optional<Movie> existingMovie = movieRepository.findByLetterboxdUrl(movie.getUrl());

            if (existingMovie.isPresent()) {
                log.info("Movie already exists in database: {}", movie.getTitle());
                return;
            }

            // Create new movie entity
            Movie newMovie = new Movie();
            newMovie.setTitle(movie.getTitle());
            newMovie.setLetterboxdUrl(movie.getUrl());
            newMovie.setYear(movie.getYear());

            movieRepository.save(newMovie);
            log.info("Saved movie to database: {} ({})", movie.getTitle(), movie.getYear());

        } catch (Exception e) {
            log.error("Failed to save movie to database: {}", e.getMessage(), e);
        }
    }
}
