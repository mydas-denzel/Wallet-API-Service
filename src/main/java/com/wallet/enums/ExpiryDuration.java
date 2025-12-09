package com.wallet.enums;

import lombok.Getter;

@Getter
public enum ExpiryDuration {
    HOUR("1H", 1),
    DAY("1D", 24),
    MONTH("1M", 24 * 30),
    YEAR("1Y", 24 * 365);

    private final String code;
    private final int hours;

    ExpiryDuration(String code, int hours) {
        this.code = code;
        this.hours = hours;
    }

    public static ExpiryDuration fromCode(String code) {
        for (ExpiryDuration duration : values()) {
            if (duration.getCode().equals(code)) {
                return duration;
            }
        }
        throw new IllegalArgumentException("Invalid expiry duration: " + code);
    }
}