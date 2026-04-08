package com.example.discordbot.utils;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.util.Map;

@Slf4j
@Component
public class TemplateUtil {

    private final MustacheFactory mustacheFactory;

    public TemplateUtil() {
        this.mustacheFactory = new DefaultMustacheFactory();
    }

    /**
     * Renders a Mustache template with the provided data.
     *
     * @param templatePath Path to the template file in classpath (e.g., "templates/movie-embed.json.mustache")
     * @param data         Map containing the data to be rendered in the template
     * @return The rendered template as a String
     * @throws RuntimeException if template loading or rendering fails
     */
    public String renderTemplate(String templatePath, Map<String, Object> data) {
        try {
            Reader reader = new InputStreamReader(new ClassPathResource(templatePath).getInputStream());
            Mustache mustache = mustacheFactory.compile(reader, templatePath);
            
            StringWriter writer = new StringWriter();
            mustache.execute(writer, data);
            
            return writer.toString();
        } catch (Exception e) {
            log.error("Failed to render template '{}': {}", templatePath, e.getMessage(), e);
            throw new RuntimeException("Failed to render template: " + templatePath, e);
        }
    }
}
