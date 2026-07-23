package com.paximum.paxassist.reservation.web;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paximum.paxassist.reservation.domain.FlightReservationDetails;
import com.paximum.paxassist.reservation.domain.HotelReservationDetails;
import com.paximum.paxassist.reservation.domain.Passenger;
import com.paximum.paxassist.reservation.domain.Reservation;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response.CancelPenalty;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response.CancellationPriceDetail;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response.CancellationService;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response.TourVisioPrice;
import com.paximum.paxassist.reservation.service.ReservationPreview;
import com.paximum.paxassist.reservation.web.dto.PreviewResponse;
import com.paximum.paxassist.reservation.web.dto.ReservationDetailResponse;
import com.paximum.paxassist.reservation.web.dto.ReservationSummaryResponse;

/** Maps entities and service results to the web response DTOs (no JPA entity is ever returned directly). */
@Component
public class ReservationWebMapper {

    public PreviewResponse toPreviewResponse(ReservationPreview preview) {
        return new PreviewResponse(
                preview.previewId(),
                preview.expiresAt(),
                preview.productType(),
                preview.totalAmount(),
                preview.currency(),
                preview.leadGuestName(),
                preview.passengerNames(),
                preview.hasHotel(),
                preview.hasFlight(),
                preview.priceChanged(),
                preview.previousAmount(),
                preview.previousCurrency(),
                preview.available(),
                toPreviewHotel(preview.hotel()),
                toPreviewFlight(preview.flight()));
    }

    private PreviewResponse.Hotel toPreviewHotel(ReservationPreview.Hotel hotel) {
        return hotel == null ? null : new PreviewResponse.Hotel(
                hotel.hotelName(), hotel.region(), hotel.stars(), hotel.boardType(),
                hotel.checkIn(), hotel.checkOut(), hotel.nights(),
                hotel.rooms(), hotel.adults(), hotel.children());
    }

    private PreviewResponse.Flight toPreviewFlight(ReservationPreview.Flight flight) {
        return flight == null ? null : new PreviewResponse.Flight(
                flight.origin(), flight.destination(), flight.airline(), flight.tripType(),
                flight.departTime(), flight.returnDepartTime(), flight.passengerCount());
    }

    public ReservationSummaryResponse toSummary(Reservation r) {
        return new ReservationSummaryResponse(
                r.getId(),
                r.getReservationNumber(),
                r.getExternalReservationNumber(),
                r.getStatus(),
                r.getProductType(),
                r.getReservationDate(),
                r.getTotalAmount(),
                r.getCurrency(),
                r.getLeadGuestName(),
                r.isGuest());
    }

    public ReservationDetailResponse toDetail(Reservation r, List<CancelPenalty> cancellationOptions) {
        return new ReservationDetailResponse(
                r.getId(),
                r.getReservationNumber(),
                r.getExternalReservationNumber(),
                r.getStatus(),
                r.getProductType(),
                r.getReservationDate(),
                r.getTotalAmount(),
                r.getCurrency(),
                r.getLeadGuestName(),
                r.getPassengers().stream().map(this::toPassenger).toList(),
                toHotel(r.getHotelDetails()),
                toFlight(r.getFlightDetails()),
                toCancellationOptions(cancellationOptions));
    }

    private ReservationDetailResponse.Passenger toPassenger(Passenger p) {
        return new ReservationDetailResponse.Passenger(
                p.getFirstName(), p.getLastName(), p.getPassengerType(), p.getAge(),
                p.getNationality(), p.getEmail(), p.getPhone());
    }

    private ReservationDetailResponse.Hotel toHotel(HotelReservationDetails h) {
        if (h == null) {
            return null;
        }
        return new ReservationDetailResponse.Hotel(
                h.getHotelName(), h.getRegion(), h.getStars(), h.getBoardType(), h.getCheckIn(), h.getCheckOut(),
                h.getRooms(), h.getAdults(), h.getChildren(), h.getNationality(), h.getPrice(), h.getCurrency());
    }

    private ReservationDetailResponse.Flight toFlight(FlightReservationDetails f) {
        if (f == null) {
            return null;
        }
        return new ReservationDetailResponse.Flight(
                f.getOrigin(), f.getDestination(), f.getAirline(), f.getTripType(), f.getDepartTime(), f.getArriveTime(),
                f.getReturnDepartTime(), f.getReturnArriveTime(), f.getStops(), f.getBaggage(), f.getPassengerCount(),
                f.getPrice(), f.getCurrency());
    }

    private List<ReservationDetailResponse.CancellationOption> toCancellationOptions(List<CancelPenalty> penalties) {
        if (penalties == null) {
            return List.of();
        }
        return penalties.stream().map(this::toCancellationOption).toList();
    }

    private ReservationDetailResponse.CancellationOption toCancellationOption(CancelPenalty p) {
        List<ReservationDetailResponse.CancellationServiceOption> services = p.services() == null
                ? List.of()
                : p.services().stream().map(this::toServiceOption).toList();
        return new ReservationDetailResponse.CancellationOption(
                p.reason() == null ? null : p.reason().id(),
                p.reason() == null ? null : p.reason().name(),
                p.reason() == null ? null : p.reason().comment(),
                p.isCancelable(),
                toMoney(p.price()),
                services);
    }

    private ReservationDetailResponse.CancellationServiceOption toServiceOption(CancellationService s) {
        return new ReservationDetailResponse.CancellationServiceOption(
                s.id(), s.code(), s.name(), s.productType(), s.isCancelable(),
                toMoney(s.price()), toPriceDetail(s.priceDetail()));
    }

    private ReservationDetailResponse.PriceDetail toPriceDetail(CancellationPriceDetail d) {
        if (d == null) {
            return null;
        }
        return new ReservationDetailResponse.PriceDetail(
                d.totalSalePrice() == null ? null : d.totalSalePrice().amount(),
                d.penalty() == null ? null : d.penalty().amount(),
                d.mainServiceFee() == null ? null : d.mainServiceFee().amount());
    }

    private ReservationDetailResponse.Money toMoney(TourVisioPrice p) {
        if (p == null) {
            return null;
        }
        return new ReservationDetailResponse.Money(p.amount(), p.currency());
    }
}
