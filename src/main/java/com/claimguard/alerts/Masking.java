package com.claimguard.alerts;

public final class Masking {

    private Masking() { }

    /** "9876543210" -> "******3210". */
    public static String mask(String mobile) {
        if (mobile == null || mobile.length() <= 4) {
            return "****";
        }
        return "*".repeat(mobile.length() - 4) + mobile.substring(mobile.length() - 4);
    }
}
