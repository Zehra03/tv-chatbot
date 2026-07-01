package com.paximum.paxassist.flight.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paximum.paxassist.flight.domain.FlightProduct;
import com.paximum.paxassist.flight.domain.FlightSearchCriteria;
import com.paximum.paxassist.flight.domain.PassengerCount;
import com.paximum.paxassist.flight.dto.FlightProductDto;
import com.paximum.paxassist.flight.dto.FlightSearchRequestDto;
import com.paximum.paxassist.flight.dto.FlightSearchResponseDto;
import com.paximum.paxassist.flight.dto.PassengerCountDto;

@Component
public class FlightSearchMapper {

    public FlightSearchCriteria toDomain(FlightSearchRequestDto dto) {
        return FlightSearchCriteria.builder()
                .origin(dto.origin())
                .destination(dto.destination())
                .departDate(dto.departDate())
                .returnDate(dto.returnDate())
                .tripType(dto.tripType())
                .passengers(toDomain(dto.passengers()))
                .currency(dto.currency())
                .nonstop(dto.nonstop())
                .preferredAirline(dto.preferredAirline())
                .build();
    }

    public FlightSearchResponseDto toResponse(FlightSearchRequestDto criteria, List<FlightProduct> products) {
        return new FlightSearchResponseDto(criteria, products.stream().map(this::toDto).toList());
    }

    private PassengerCount toDomain(PassengerCountDto dto) {
        return PassengerCount.builder()
                .adults(dto.adults())
                .children(dto.children())
                .infants(dto.infants())
                .build();
    }

    private FlightProductDto toDto(FlightProduct product) {
        return new FlightProductDto(
                product.getId(),
                product.getAirline(),
                product.getFlightNumber(),
                product.getOrigin(),
                product.getDestination(),
                product.getDepartTime(),
                product.getArriveTime(),
                product.getReturnDepartTime(),
                product.getReturnArriveTime(),
                product.getStops(),
                product.getBaggage(),
                product.getPrice(),
                product.getCurrency());
    }
}
