package com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.request;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * SetReservationInfo — attach traveller + customer details to an open transaction. CONFIRMED payload.
 *
 * <p><b>PII WARNING:</b> this payload carries passenger/customer PII (names, birth dates, passport /
 * identity numbers, email, phone, address). It must NEVER be logged — the booking client never logs
 * request bodies.
 *
 * <p>All nested records use {@code @JsonInclude(NON_NULL)} so absent optional fields are omitted;
 * the TourVisio samples send empty strings ({@code ""}) rather than nulls for "known but blank"
 * fields, so the caller should pass {@code ""} where that semantics is required.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SetReservationInfoRequest(
        @JsonProperty("transactionId") String transactionId,
        @JsonProperty("travellers") List<Traveller> travellers,
        @JsonProperty("customerInfo") CustomerInfo customerInfo,
        @JsonProperty("reservationNote") String reservationNote,
        @JsonProperty("agencyReservationNumber") String agencyReservationNumber) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Traveller(
            @JsonProperty("travellerId") String travellerId,
            @JsonProperty("type") Integer type,
            @JsonProperty("title") Integer title,
            @JsonProperty("academicTitle") AcademicTitle academicTitle,
            @JsonProperty("passengerType") Integer passengerType,
            @JsonProperty("name") String name,
            @JsonProperty("surname") String surname,
            @JsonProperty("isLeader") Boolean isLeader,
            @JsonProperty("birthDate") String birthDate,
            @JsonProperty("nationality") Nationality nationality,
            @JsonProperty("identityNumber") String identityNumber,
            @JsonProperty("passportInfo") PassportInfo passportInfo,
            @JsonProperty("address") TravellerAddress address,
            @JsonProperty("orderNumber") Integer orderNumber,
            @JsonProperty("documents") List<Object> documents,
            @JsonProperty("insertFields") List<Object> insertFields,
            @JsonProperty("status") Integer status,
            @JsonProperty("gender") Integer gender) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AcademicTitle(@JsonProperty("id") Integer id) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Nationality(@JsonProperty("twoLetterCode") String twoLetterCode) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PassportInfo(
            @JsonProperty("serial") String serial,
            @JsonProperty("number") String number,
            @JsonProperty("expireDate") String expireDate,
            @JsonProperty("issueDate") String issueDate,
            @JsonProperty("citizenshipCountryCode") String citizenshipCountryCode) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ContactPhone(
            @JsonProperty("countryCode") String countryCode,
            @JsonProperty("areaCode") String areaCode,
            @JsonProperty("phoneNumber") String phoneNumber) {
    }

    /** {@code { id, name }} reference used for city / country. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PlaceRef(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name) {
    }

    /** Traveller address — uses a structured {@link ContactPhone}. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TravellerAddress(
            @JsonProperty("contactPhone") ContactPhone contactPhone,
            @JsonProperty("email") String email,
            @JsonProperty("address") String address,
            @JsonProperty("zipCode") String zipCode,
            @JsonProperty("city") PlaceRef city,
            @JsonProperty("country") PlaceRef country) {
    }

    /** Customer address — uses a flat {@code phone} string (not a structured contactPhone). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CustomerAddress(
            @JsonProperty("city") PlaceRef city,
            @JsonProperty("country") PlaceRef country,
            @JsonProperty("email") String email,
            @JsonProperty("phone") String phone,
            @JsonProperty("address") String address,
            @JsonProperty("zipCode") String zipCode) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TaxInfo(
            @JsonProperty("taxOffice") String taxOffice,
            @JsonProperty("taxNumber") String taxNumber) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CustomerInfo(
            @JsonProperty("isCompany") Boolean isCompany,
            @JsonProperty("address") CustomerAddress address,
            @JsonProperty("taxInfo") TaxInfo taxInfo,
            @JsonProperty("title") Integer title,
            @JsonProperty("name") String name,
            @JsonProperty("surname") String surname,
            @JsonProperty("birthDate") String birthDate,
            @JsonProperty("identityNumber") String identityNumber) {
    }
}
