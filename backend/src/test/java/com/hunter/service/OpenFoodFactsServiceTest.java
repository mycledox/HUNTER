package com.hunter.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenFoodFactsServiceTest {

    @Test
    void acceptsValidGtinLengthsAndCheckDigits() {
        assertThat(OpenFoodFactsService.isValidGtin("012345678905")).isTrue();
        assertThat(OpenFoodFactsService.isValidGtin("96385074")).isTrue();
        assertThat(OpenFoodFactsService.isValidGtin("4006381333931")).isTrue();
        assertThat(OpenFoodFactsService.isValidGtin("00012345678905")).isTrue();
    }

    @Test
    void rejectsMalformedOrInvalidGtinCheckDigits() {
        assertThat(OpenFoodFactsService.isValidGtin("012345678904")).isFalse();
        assertThat(OpenFoodFactsService.isValidGtin("1234567")).isFalse();
        assertThat(OpenFoodFactsService.isValidGtin("abcdefghijk1")).isFalse();
    }

    @Test
    void readsNumericNutrientValuesFromProductRecords() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        assertThat(OpenFoodFactsService.text(mapper.readTree("539.0"))).isEqualTo("539.0");
        assertThat(OpenFoodFactsService.text(mapper.readTree("\"400 g\""))).isEqualTo("400 g");
        assertThat(OpenFoodFactsService.text(mapper.readTree("null"))).isEmpty();
    }
}
