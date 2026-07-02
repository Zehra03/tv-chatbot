package com.paximum.paxassist.reservation;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReservationService {

    public ReservationPreviewResponse preview(ReservationPreviewRequest request, Long userId) {
        // TODO: call TourVisio to fetch price/availability snapshot without booking
        throw new UnsupportedOperationException("Rezervasyon önizleme henüz tamamlanmadı");
    }

    public ReservationResponse create(CreateReservationRequest request, Long userId) {
        // TODO: call TourVisio to book, persist reservation + passengers to DB
        throw new UnsupportedOperationException("Rezervasyon oluşturma henüz tamamlanmadı");
    }

    public List<ReservationResponse> findAll(Long userId) {
        // TODO: query reservations table filtered by user_id
        throw new UnsupportedOperationException("Rezervasyon listesi henüz tamamlanmadı");
    }

    public ReservationResponse findById(Long id, Long userId) {
        // TODO: query reservations table, enforce ownership
        throw new UnsupportedOperationException("Rezervasyon detayı henüz tamamlanmadı");
    }

    public ReservationResponse cancel(Long id, Long userId) {
        // TODO: verify ownership, update status to 'cancelled' in DB, call TourVisio cancellation
        throw new UnsupportedOperationException("Rezervasyon iptali henüz tamamlanmadı");
    }
}
