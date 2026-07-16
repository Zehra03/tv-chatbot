package com.paximum.paxassist.guard;

import lombok.Getter;

@Getter
public class GuardBlockedException extends RuntimeException {

    private final String detailedReason;

    public GuardBlockedException(String message, String detailedReason) {
        super(message);
        this.detailedReason = detailedReason;
    }
}
