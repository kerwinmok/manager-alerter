package com.kerwinmok.manageralerter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonPresenceGateTest {
    @Test
    void ringsOnceAfterConfirmedEntryAndNotAgainWhilePresent() {
        PersonPresenceGate gate = new PersonPresenceGate(10_000, 1_500);

        assertFalse(gate.update(true, 0).present());
        assertTrue(gate.update(true, 200).ring());
        assertFalse(gate.update(true, 400).ring());
        assertFalse(gate.update(true, 800).ring());
    }

    @Test
    void requiresAbsenceAndCooldownBeforeRingingAgain() {
        PersonPresenceGate gate = new PersonPresenceGate(10_000, 1_500);

        gate.update(true, 0);
        assertTrue(gate.update(true, 200).ring());
        assertFalse(gate.update(false, 1_800).present());
        assertFalse(gate.update(true, 5_000).present());
        assertFalse(gate.update(true, 5_200).ring());
        assertFalse(gate.update(false, 7_000).present());
        assertFalse(gate.update(true, 10_500).present());
        assertTrue(gate.update(true, 10_700).ring());
    }

    @Test
    void briefDetectionGapsDoNotEndAPresence() {
        PersonPresenceGate gate = new PersonPresenceGate(10_000, 1_500);

        gate.update(true, 100);
        assertTrue(gate.update(true, 200).ring());
        assertTrue(gate.update(false, 700).present());
        assertTrue(gate.update(true, 1_000).present());
        assertFalse(gate.update(true, 1_000).ring());
    }
}