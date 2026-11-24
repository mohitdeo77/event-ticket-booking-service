package com.mohitdeo.booking.api;

import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Renders business errors as problem details. Framework errors (validation, missing headers,
 * malformed JSON) are handled by {@link ResponseEntityExceptionHandler}.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BookingException.class)
    ResponseEntity<ProblemDetail> handleBookingException(BookingException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
        problem.setTitle(ex.title());
        ex.properties().forEach(problem::setProperty);
        return ResponseEntity.status(ex.status()).body(problem);
    }
}
