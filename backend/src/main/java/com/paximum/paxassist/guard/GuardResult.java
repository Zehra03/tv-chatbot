package com.paximum.paxassist.guard;

import lombok.Getter;

@Getter
public class GuardResult {

    private final boolean blocked;
    private final String reason;

    public GuardResult(boolean blocked, String reason) {
        this.blocked = blocked;
        this.reason = reason;
    }
}
