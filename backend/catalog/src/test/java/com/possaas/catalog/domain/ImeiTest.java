package com.possaas.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ImeiTest {

    @Test
    void acceptsValidImei() {
        // 490154203237518 is the well-known IMEI example with a correct check digit.
        assertThat(Imei.isValid("490154203237518")).isTrue();
    }

    @Test
    void rejectsImeiWithWrongCheckDigit() {
        assertThat(Imei.isValid("490154203237519")).isFalse();
    }

    @Test
    void rejectsWrongLength() {
        assertThat(Imei.isValid("49015420323751")).isFalse();
        assertThat(Imei.isValid("4901542032375188")).isFalse();
    }

    @Test
    void rejectsBlankAndNull() {
        assertThat(Imei.isValid(null)).isFalse();
        assertThat(Imei.isValid("   ")).isFalse();
    }

    @Test
    void normalizesScannerPunctuation() {
        assertThat(Imei.normalize("49-0154 203237518")).isEqualTo("490154203237518");
        assertThat(Imei.isValid("49-0154 203237518")).isTrue();
    }

    @Test
    void normalizeReturnsNullForBlank() {
        assertThat(Imei.normalize("")).isNull();
        assertThat(Imei.normalize("---")).isNull();
    }
}
