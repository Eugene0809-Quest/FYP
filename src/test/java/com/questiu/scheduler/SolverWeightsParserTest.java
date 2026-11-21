package com.questiu.scheduler;

import com.questiu.scheduler.solver.SolverWeights;
import com.questiu.scheduler.solver.SolverWeightsParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What an administrator types on the Solver Weights screen. No JavaFX, database or OR-Tools needed. */
class SolverWeightsParserTest {

    @Test
    void theDefaultsTypedBackInGiveTheDefaults() {
        SolverWeightsParser.Result r = SolverWeightsParser.parse("10000", "5", "2", "100", "1");
        assertNull(r.error);
        SolverWeights d = SolverWeights.defaults();
        assertEquals(d.unfilledSen(), r.weights.unfilledSen());
        assertEquals(d.preferenceSenPerLevel(), r.weights.preferenceSenPerLevel());
        assertEquals(d.workloadPerHundredthHour(), r.weights.workloadPerHundredthHour());
        assertEquals(d.overtimePerHundredthHour(), r.weights.overtimePerHundredthHour());
    }

    @Test
    void commasSpacesAndDecimalsAreAccepted() {
        SolverWeightsParser.Result r = SolverWeightsParser.parse(" 12,345.67 ", "8", "2.50", "90", "1.5");
        assertNull(r.error);
        assertEquals(1_234_567, r.weights.unfilledSen());
        assertEquals(250, r.weights.preferenceSenPerLevel());
    }

    @Test
    void emptyAndNonNumbersGiveAClearSentence() {
        SolverWeightsParser.Result empty = SolverWeightsParser.parse("10000", "", "2", "100", "1");
        assertNotNull(empty.error);
        assertTrue(empty.error.contains("Fairness"));
        SolverWeightsParser.Result text = SolverWeightsParser.parse("lots", "5", "2", "100", "1");
        assertNotNull(text.error);
        assertTrue(text.error.contains("not a number"));
        assertNull(text.weights);
    }

    @Test
    void wholeNumberFieldsRefuseCents() {
        SolverWeightsParser.Result r = SolverWeightsParser.parse("10000", "2.5", "2", "100", "1");
        assertNotNull(r.error);
        assertTrue(r.error.contains("whole number"));
        assertNull(SolverWeightsParser.parse("10000", "5.00", "2", "100", "1").error); // 5.00 is whole
    }

    @Test
    void notAllowedValuesAreRefusedWithTheRuleThatBrokeIt() {
        assertNotNull(SolverWeightsParser.parse("10000", "5", "-2", "100", "1").error);
        assertNotNull(SolverWeightsParser.parse("10000", "5", "2", "100", "1000").error);
        assertNotNull(SolverWeightsParser.parse("5000000", "5", "2", "100", "1").error);
    }

    @Test
    void plainTextForTheFields() {
        assertEquals("2", SolverWeightsParser.plain(2.0));
        assertEquals("10000", SolverWeightsParser.plain(10000.0));
        assertEquals("12345.67", SolverWeightsParser.plain(12345.67));
        assertEquals("0", SolverWeightsParser.plain(0.0));
        assertEquals("1.5", SolverWeightsParser.plain(1.5));
    }
}
