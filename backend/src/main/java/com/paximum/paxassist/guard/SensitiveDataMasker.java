package com.paximum.paxassist.guard;

final class SensitiveDataMasker {

    private static final String CREDIT_CARD_MASK = "**** **** **** ****";
    private static final String TCKN_MASK = "***********";
    private static final String IBAN_MASK = "[MASKED-IBAN]";

    private SensitiveDataMasker() {
    }

    static String mask(String input, String reason) {
        if (reason == null) {
            return input;
        }
        if (reason.contains("Credit Card")) {
            return GuardPatterns.CREDIT_CARD.matcher(input).replaceAll(CREDIT_CARD_MASK);
        }
        if (reason.contains("TCKN")) {
            return GuardPatterns.TCKN.matcher(input).replaceAll(TCKN_MASK);
        }
        if (reason.contains("IBAN")) {
            return GuardPatterns.IBAN.matcher(input).replaceAll(IBAN_MASK);
        }
        return input;
    }
}
