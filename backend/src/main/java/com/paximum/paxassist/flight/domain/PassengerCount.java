package com.paximum.paxassist.flight.domain;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PassengerCount {
    private final int adults;
    private final int children;
    private final int infants;
}