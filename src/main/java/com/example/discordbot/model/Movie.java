package com.example.discordbot.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "movies", indexes = {
    @Index(name = "idx_movies_title_trgm", columnList = "title"),
    @Index(name = "idx_movies_letterboxd_url", columnList = "letterboxd_url", unique = true)
})
@Data
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "letterboxd_url", nullable = false, length = 1000)
    private String letterboxdUrl;

    @Column(name = "year", length = 10)
    private String year;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
