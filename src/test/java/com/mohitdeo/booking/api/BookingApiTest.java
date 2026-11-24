package com.mohitdeo.booking.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.mohitdeo.booking.support.TestInfrastructure;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestInfrastructure.class)
class BookingApiTest {

    @Autowired
    MockMvc mvc;

    @Test
    void fullBookingFlowOverHttp() throws Exception {
        String eventId = createEvent();
        String seatId = firstSeatId(eventId);
        String body = holdBody(seatId);

        String created = mvc.perform(post("/api/events/{id}/reservations", eventId)
                        .header("Idempotency-Key", "api-flow-1")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("HELD"))
                .andReturn().getResponse().getContentAsString();
        String reservationId = JsonPath.read(created, "$.id");

        // Client retry with the same key: same reservation, flagged as a replay
        mvc.perform(post("/api/events/{id}/reservations", eventId)
                        .header("Idempotency-Key", "api-flow-1")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(reservationId));

        // A different user cannot take the held seat
        mvc.perform(post("/api/events/{id}/reservations", eventId)
                        .header("Idempotency-Key", "api-flow-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"someone-else\",\"seatIds\":[\"" + seatId + "\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Seats unavailable"));

        mvc.perform(post("/api/reservations/{id}/confirm", reservationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mvc.perform(get("/api/events/{id}/seats", eventId))
                .andExpect(jsonPath("$[0].status").value("BOOKED"));
    }

    @Test
    void holdWithoutIdempotencyKeyIsRejected() throws Exception {
        String eventId = createEvent();
        mvc.perform(post("/api/events/{id}/reservations", eventId)
                        .contentType(MediaType.APPLICATION_JSON).content(holdBody(firstSeatId(eventId))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidBodyIsRejected() throws Exception {
        String eventId = createEvent();
        mvc.perform(post("/api/events/{id}/reservations", eventId)
                        .header("Idempotency-Key", "bad-body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"\",\"seatIds\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownReservationReturns404() throws Exception {
        mvc.perform(get("/api/reservations/{id}", "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Reservation not found"));
    }

    private String createEvent() throws Exception {
        String json = mvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Comedy Night","venue":"Hall 1","startsAt":"2026-05-01T18:30:00Z",
                                 "rows":1,"seatsPerRow":3}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalSeats").value(3))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private String firstSeatId(String eventId) throws Exception {
        String json = mvc.perform(get("/api/events/{id}/seats", eventId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$[0].id");
    }

    private static String holdBody(String seatId) {
        return "{\"userId\":\"user-42\",\"seatIds\":[\"" + seatId + "\"]}";
    }
}
