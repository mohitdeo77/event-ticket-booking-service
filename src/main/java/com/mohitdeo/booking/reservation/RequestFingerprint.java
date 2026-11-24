package com.mohitdeo.booking.reservation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.UUID;
import java.util.stream.Collectors;

/** Stable hash of a hold request, the same no matter what order the seat ids arrive in. */
final class RequestFingerprint {

    private RequestFingerprint() {
    }

    static String of(UUID eventId, String userId, Collection<UUID> seatIds) {
        String canonical = eventId + "|" + userId + "|" + seatIds.stream()
                .sorted()
                .map(UUID::toString)
                .collect(Collectors.joining(","));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
