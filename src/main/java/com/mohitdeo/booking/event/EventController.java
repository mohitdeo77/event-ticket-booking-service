package com.mohitdeo.booking.event;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mohitdeo.booking.event.EventDtos.CreateEventRequest;
import com.mohitdeo.booking.event.EventDtos.EventView;
import com.mohitdeo.booking.event.EventDtos.SeatView;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<EventView> create(@Valid @RequestBody CreateEventRequest request) {
        EventView event = eventService.create(request);
        return ResponseEntity.created(URI.create("/api/events/" + event.id())).body(event);
    }

    @GetMapping("/{eventId}/seats")
    public List<SeatView> seats(@PathVariable UUID eventId) {
        return eventService.seatMap(eventId);
    }
}
