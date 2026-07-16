package com.paximum.paxassist.reservation.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.paximum.paxassist.reservation.domain.PassengerType;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.request.AddServicesRequest;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.request.BeginTransactionWithOfferRequest;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.request.CommitTransactionRequest;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.request.SetReservationInfoRequest;
import com.paximum.paxassist.reservation.service.command.PreviewReservationCommand;

/**
 * Maps a frozen {@link PreviewReservationCommand} snapshot into the TourVisio booking request DTOs
 * (ticket 2). Pure mapping — no I/O, no persistence.
 *
 * <p>TourVisio-only fields that have no equivalent in our validated input are filled with the fixed
 * defaults seen in the confirmed samples (documented inline). A couple of numeric enum mappings are
 * best-effort and flagged (see {@link #passengerType}).
 */
@Component
public class ReservationTourVisioRequestMapper {

    private static final String DEFAULT_CULTURE = "en-US";
    private static final DateTimeFormatter TV_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter TV_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    public BeginTransactionWithOfferRequest toBeginRequest(PreviewReservationCommand command) {
        return new BeginTransactionWithOfferRequest(command.offerIds(), command.currency(), culture(command));
    }

    /** One AddServices call batching all additional offers; empty if there are none. */
    public Optional<AddServicesRequest> toAddServicesRequest(PreviewReservationCommand command, String transactionId) {
        List<PreviewReservationCommand.AddOffer> extras = command.additionalOffers();
        if (extras == null || extras.isEmpty()) {
            return Optional.empty();
        }
        List<AddServicesRequest.Offer> offers = extras.stream()
                .map(o -> new AddServicesRequest.Offer(o.offerId(), o.travellerIds()))
                .toList();
        return Optional.of(new AddServicesRequest(transactionId, offers, command.currency(), culture(command)));
    }

    public SetReservationInfoRequest toSetReservationInfoRequest(PreviewReservationCommand command, String transactionId) {
        List<SetReservationInfoRequest.Traveller> travellers = mapTravellers(command);
        SetReservationInfoRequest.CustomerInfo customer = mapCustomer(command.customer());
        return new SetReservationInfoRequest(
                transactionId,
                travellers,
                customer,
                command.reservationNote(),
                command.agencyReservationNumber());
    }

    /**
     * Commit request with the fixed dummy {@code PaymentInformation} block — no real payment is
     * captured (SanTSG test account). Amount/currency come from the frozen snapshot; Reference/Token
     * are fresh UUIDs.
     */
    public CommitTransactionRequest toCommitRequest(PreviewReservationCommand command, String transactionId) {
        CommitTransactionRequest.PaymentPrice price =
                new CommitTransactionRequest.PaymentPrice(command.totalAmount(), command.currency());
        CommitTransactionRequest.PaymentInformation payment = new CommitTransactionRequest.PaymentInformation(
                "SanTSG",
                1,
                price,
                "1",
                LocalDate.now().format(TV_DATE),
                "0",
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString());
        return new CommitTransactionRequest(transactionId, payment);
    }

    // --- helpers -----------------------------------------------------------------------------

    private List<SetReservationInfoRequest.Traveller> mapTravellers(PreviewReservationCommand command) {
        List<PreviewReservationCommand.Traveller> src = command.travellers();
        return java.util.stream.IntStream.range(0, src.size())
                .mapToObj(i -> mapTraveller(src.get(i), i + 1))
                .toList();
    }

    private SetReservationInfoRequest.Traveller mapTraveller(PreviewReservationCommand.Traveller t, int order) {
        String travellerId = t.travellerId() != null ? t.travellerId() : String.valueOf(order);
        return new SetReservationInfoRequest.Traveller(
                travellerId,
                1, // type: fixed "person" type per samples
                t.title(),
                null, // academicTitle: not collected in our input
                passengerType(t.passengerType()),
                t.firstName(),
                t.lastName(),
                t.leader(),
                formatDateTime(t.birthDate()),
                t.nationalityCode() == null ? null : new SetReservationInfoRequest.Nationality(t.nationalityCode()),
                t.identityNumber(),
                mapPassport(t.passport()),
                mapTravellerAddress(t),
                order,
                List.of(), // documents: none collected
                List.of(), // insertFields: none collected
                0, // status
                t.gender() != null ? t.gender() : 0);
    }

    private SetReservationInfoRequest.PassportInfo mapPassport(PreviewReservationCommand.Passport p) {
        if (p == null) {
            return null;
        }
        return new SetReservationInfoRequest.PassportInfo(
                p.serial(), p.number(), formatDateTime(p.expireDate()), formatDateTime(p.issueDate()), p.citizenshipCountryCode());
    }

    private SetReservationInfoRequest.TravellerAddress mapTravellerAddress(PreviewReservationCommand.Traveller t) {
        PreviewReservationCommand.Address a = t.address();
        PreviewReservationCommand.ContactPhone cp = t.contactPhone();
        SetReservationInfoRequest.ContactPhone phone = cp == null ? null
                : new SetReservationInfoRequest.ContactPhone(cp.countryCode(), cp.areaCode(), cp.phoneNumber());
        if (a == null) {
            return phone == null ? null : new SetReservationInfoRequest.TravellerAddress(phone, null, null, null, null, null);
        }
        return new SetReservationInfoRequest.TravellerAddress(
                phone,
                a.email(),
                a.line(),
                a.zipCode(),
                new SetReservationInfoRequest.PlaceRef(a.cityId(), a.cityName()),
                new SetReservationInfoRequest.PlaceRef(a.countryId(), a.countryName()));
    }

    private SetReservationInfoRequest.CustomerInfo mapCustomer(PreviewReservationCommand.Customer c) {
        if (c == null) {
            return null;
        }
        SetReservationInfoRequest.CustomerAddress address = new SetReservationInfoRequest.CustomerAddress(
                new SetReservationInfoRequest.PlaceRef(null, c.cityName()),
                new SetReservationInfoRequest.PlaceRef(null, c.countryName()),
                c.email(),
                c.phone(),
                c.line(),
                c.zipCode());
        SetReservationInfoRequest.TaxInfo taxInfo =
                new SetReservationInfoRequest.TaxInfo(c.taxOffice(), c.taxNumber());
        return new SetReservationInfoRequest.CustomerInfo(
                c.company(), address, taxInfo, c.title(), c.name(), c.surname(), formatDate(c.birthDate()), c.identityNumber());
    }

    /**
     * TourVisio numeric passengerType. Best-effort mapping (ADULT=1, CHILD=2) — flagged; confirm the
     * numeric codes against TourVisio docs.
     */
    private Integer passengerType(PassengerType type) {
        if (type == null) {
            return null;
        }
        return type == PassengerType.CHILD ? 2 : 1;
    }

    private String culture(PreviewReservationCommand command) {
        return command.culture() != null ? command.culture() : DEFAULT_CULTURE;
    }

    /** LocalDate -> "yyyy-MM-ddT00:00:00" (the datetime format the traveller/passport fields use). */
    private String formatDateTime(LocalDate date) {
        return date == null ? null : date.atStartOfDay().format(TV_DATE_TIME);
    }

    /** LocalDate -> "yyyy-MM-dd" (customerInfo.birthDate uses a plain date in the sample). */
    private String formatDate(LocalDate date) {
        return date == null ? null : date.format(TV_DATE);
    }
}
