package com.mohitdeo.booking.event;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "events")
public class Event {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String venue;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Event() {
    }

    public Event(String name, String venue, Instant startsAt, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.venue = venue;
        this.startsAt = startsAt;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getVenue() {
        return venue;
    }

    public Instant getStartsAt() {
        return startsAt;
    }
}
