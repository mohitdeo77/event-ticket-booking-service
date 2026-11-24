package com.mohitdeo.booking.event;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mohitdeo.booking.api.Errors.NotFoundException;
import com.mohitdeo.booking.event.EventDtos.CreateEventRequest;
import com.mohitdeo.booking.event.EventDtos.EventView;
import com.mohitdeo.booking.event.EventDtos.SeatView;
import com.mohitdeo.booking.seat.Seat;
import com.mohitdeo.booking.seat.SeatRepository;

@Service
public class EventService {

    private final EventRepository events;
    private final SeatRepository seats;
    private final Clock clock;

    public EventService(EventRepository events, SeatRepository seats, Clock clock) {
        this.events = events;
        this.seats = seats;
        this.clock = clock;
    }

    /** Creates an event with a rectangular seat map: rows A, B, C... each with N seats. */
    @Transactional
    public EventView create(CreateEventRequest request) {
        Event event = events.save(new Event(request.name(), request.venue(), request.startsAt(), clock.instant()));

        List<Seat> seatMap = new ArrayList<>(request.rows() * request.seatsPerRow());
        for (int row = 0; row < request.rows(); row++) {
            String rowLabel = String.valueOf((char) ('A' + row));
            for (int number = 1; number <= request.seatsPerRow(); number++) {
                seatMap.add(new Seat(event.getId(), rowLabel, number));
            }
        }
        seats.saveAll(seatMap);
        return EventView.from(event, seatMap.size());
    }

    @Transactional(readOnly = true)
    public List<SeatView> seatMap(UUID eventId) {
        if (!events.existsById(eventId)) {
            throw new NotFoundException("Event", eventId);
        }
        return seats.findByEventIdOrderByRowLabelAscSeatNumberAsc(eventId).stream()
                .map(SeatView::from)
                .toList();
    }
}
