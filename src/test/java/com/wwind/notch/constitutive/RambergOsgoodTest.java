package com.wwind.notch.constitutive;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RambergOsgoodTest {

    private final RambergOsgood law = new RambergOsgood(200_000, 1_200, 0.2);

    @Test
    void strainIsHookePlusPowerLawWithExponentOneOverN() {
        double stress = 400.0;
        double elastic = stress / 200_000.0;
        // The trap: exponent must be 1/n = 5, never n = 0.2.
        double plastic = Math.pow(stress / 1_200.0, 1.0 / 0.2);
        assertEquals(elastic + plastic, law.totalStrain(stress), 1e-15);
        assertEquals(5.0, law.plasticExponent(), 0.0);
    }

    @Test
    void plasticStrainIsOddSymmetric() {
        assertEquals(law.plasticStrain(400), -law.plasticStrain(-400), 1e-15);
        assertEquals(law.totalStrain(400), -law.totalStrain(-400), 1e-15);
    }

    @Test
    void proportionalLimitUsesOffsetStrainAnalytically() {
        // sigma_p = K * 0.002^n, and plastic strain at sigma_p equals 0.002.
        double sigmaP = law.proportionalLimitStress();
        assertEquals(1_200.0 * Math.pow(0.002, 0.2), sigmaP, 1e-9);
        assertEquals(RambergOsgood.OFFSET_STRAIN, law.plasticStrain(sigmaP), 1e-12);
    }

    @Test
    void zeroStressGivesZeroStrain() {
        assertEquals(0.0, law.totalStrain(0.0), 0.0);
    }

    @Test
    void nonPositiveConstantsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RambergOsgood(0, 1200, 0.2));
        assertThrows(IllegalArgumentException.class, () -> new RambergOsgood(200_000, -1, 0.2));
        assertThrows(IllegalArgumentException.class, () -> new RambergOsgood(200_000, 1200, 0.0));
    }

    @Test
    void plasticStrainIsSmallBelowProportionalLimit() {
        assertTrue(law.plasticStrain(300.0) < RambergOsgood.OFFSET_STRAIN);
    }
}
