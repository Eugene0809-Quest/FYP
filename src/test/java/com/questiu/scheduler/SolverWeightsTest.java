package com.questiu.scheduler;

import com.questiu.scheduler.solver.LabourCost;
import com.questiu.scheduler.solver.SolverWeights;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The weights object needs no OR-Tools native library, so these run anywhere. */
class SolverWeightsTest {

    @Test
    void defaultsEqualTheNumbersThatWereHardCodedBefore() {
        SolverWeights w = SolverWeights.defaults();
        assertEquals(1_000_000, w.unfilledSen());          // RM10,000
        assertEquals(5, w.workloadPerHundredthHour());     // RM5 per hour of spread
        assertEquals(200, w.preferenceSenPerLevel());      // RM2 per level
        assertEquals(100, w.overtimePerHundredthHour());   // RM100 per overtime hour
        assertEquals(4800, w.scaleCost(4800));             // cost counts at face value
        assertNull(w.problem());
    }

    @Test
    void ringgitConvertToSenWithoutLosingCents() {
        SolverWeights w = new SolverWeights(12_345.67, 7, 2.50, 90, 1.5);
        assertEquals(1_234_567, w.unfilledSen());
        assertEquals(250, w.preferenceSenPerLevel());
        assertEquals(7200, w.scaleCost(4800));
        assertNull(w.problem());
    }

    @Test
    void absurdValuesAreRejected() {
        assertNotNull(new SolverWeights(-1, 5, 2, 100, 1).problem());
        assertNotNull(new SolverWeights(10_000, -5, 2, 100, 1).problem());
        assertNotNull(new SolverWeights(10_000, 5, -2, 100, 1).problem());
        assertNotNull(new SolverWeights(10_000, 5, 2, 100, -1).problem());
        assertNotNull(new SolverWeights(10_000, 5, 2, 100, 1000).problem());
        assertNotNull(new SolverWeights(Double.NaN, 5, 2, 100, 1).problem());
        assertNotNull(new SolverWeights(10_000, 5, Double.POSITIVE_INFINITY, 100, 1).problem());
        assertNotNull(new SolverWeights(2_000_000, 5, 2, 100, 1).problem());
    }

    @Test
    void zeroIsAllowedForEveryWeight() {
        assertNull(new SolverWeights(0, 0, 0, 0, 0).problem());
    }

    @Test
    void aTooSmallUnfilledPriceIsLiftedAboveTheDearestShift() {
        // Configured RM1 per empty slot, dearest position RM1,600 (160,000 sen): the safety rule wins.
        assertEquals(1_600_000, LabourCost.unfilledPenalty(160_000, 100));
        // A generous configured price is respected as it is.
        assertEquals(5_000_000, LabourCost.unfilledPenalty(160_000, 5_000_000));
    }
}
