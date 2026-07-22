package com.paximum.paxassist.admin.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.paximum.paxassist.admin.dto.AdminReservationResponse;
import com.paximum.paxassist.auth.domain.User;
import com.paximum.paxassist.auth.repository.UserRepository;
import com.paximum.paxassist.reservation.domain.ProductType;
import com.paximum.paxassist.reservation.domain.Reservation;
import com.paximum.paxassist.reservation.domain.ReservationStatus;
import com.paximum.paxassist.reservation.repository.ReservationRepository;

/**
 * Admin reservation queries: every booking in the system, with the account that owns it.
 *
 * <p>The owner is resolved here rather than through a JPA relation because {@code Reservation.userId}
 * is deliberately a plain {@code Long} — the reservation module does not depend on the auth module's
 * entity. Joining the two is an admin-view concern, so it lives in the admin module.
 */
@Service
public class AdminReservationService {

    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;

    public AdminReservationService(ReservationRepository reservationRepository,
                                   UserRepository userRepository) {
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
    }

    /**
     * One page of reservations across ALL customers — member and guest alike — optionally narrowed by
     * PNR text, status and product type.
     *
     * <p>Owners are fetched in a single batch keyed by the ids on the page, not per row: a
     * lookup inside the mapping loop would be one query per reservation (N+1), which on a 20-row page
     * is 20 round trips that grow with the page size.
     */
    @Transactional(readOnly = true)
    public Page<AdminReservationResponse> search(String pnr, ReservationStatus status,
                                                 ProductType productType, Pageable pageable) {
        Page<Reservation> page = reservationRepository.searchForAdmin(pnr, status, productType, pageable);

        Set<Long> ownerIds = page.getContent().stream()
                .map(Reservation::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, User> owners = ownerIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(ownerIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));

        return page.map(r -> toRow(r, owners));
    }

    private static AdminReservationResponse toRow(Reservation r, Map<Long, User> owners) {
        // A guest booking has no account; a member booking whose user row is missing (deleted account)
        // also lands here as null rather than throwing — the reservation still has to be listable.
        User owner = r.getUserId() == null ? null : owners.get(r.getUserId());
        return new AdminReservationResponse(
                r.getId(),
                r.getReservationNumber(),
                r.getExternalReservationNumber(),
                r.getStatus(),
                r.getProductType(),
                r.getReservationDate(),
                r.getTotalAmount(),
                r.getCurrency(),
                r.getLeadGuestName(),
                r.isGuest(),
                owner == null ? null : owner.getEmail(),
                owner == null ? null : owner.getDisplayName());
    }

    /** Reservation counts per product type, keyed by the enum's lowercase JSON form. */
    @Transactional(readOnly = true)
    public Map<String, Long> countsByProductType() {
        List<Object[]> rows = reservationRepository.countByProductType();
        return rows.stream()
                .filter(row -> row[0] != null && row[1] != null)
                .collect(Collectors.toMap(
                        row -> ((ProductType) row[0]).toJson(),
                        row -> (Long) row[1]));
    }
}
