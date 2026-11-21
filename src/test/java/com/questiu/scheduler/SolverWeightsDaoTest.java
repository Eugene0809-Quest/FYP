package com.questiu.scheduler;

import com.questiu.scheduler.dao.SolverWeightsDao;
import com.questiu.scheduler.solver.SolverWeights;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How stored rule weights become solver weights. No database or OR-Tools needed. */
class SolverWeightsDaoTest {

    private static Map<String, BigDecimal> migrated() {
        Map<String, BigDecimal> m = new HashMap<>();
        m.put("OVERTIME", new BigDecimal("100.00"));
        m.put("WORKLOAD", new BigDecimal("5.00"));
        m.put("PREFERENCE", new BigDecimal("2.00"));
        m.put("UNFILLED", new BigDecimal("10000.00"));
        m.put("LABOUR_COST", new BigDecimal("1.00"));
        return m;
    }

    @Test
    void migratedDefaultsGiveExactlyTheBuiltInDefaults() {
        SolverWeightsDao.Loaded l = SolverWeightsDao.fromRules(migrated());
        assertNull(l.note);
        SolverWeights d = SolverWeights.defaults();
        assertEquals(d.unfilledSen(), l.weights.unfilledSen());
        assertEquals(d.workloadPerHundredthHour(), l.weights.workloadPerHundredthHour());
        assertEquals(d.preferenceSenPerLevel(), l.weights.preferenceSenPerLevel());
        assertEquals(d.overtimePerHundredthHour(), l.weights.overtimePerHundredthHour());
        assertEquals(d.scaleCost(4800), l.weights.scaleCost(4800));
    }

    @Test
    void customValuesAreRead() {
        Map<String, BigDecimal> m = migrated();
        m.put("PREFERENCE", new BigDecimal("10.00"));
        m.put("WORKLOAD", new BigDecimal("8.00"));
        m.put("LABOUR_COST", new BigDecimal("1.50"));
        SolverWeightsDao.Loaded l = SolverWeightsDao.fromRules(m);
        assertNull(l.note);
        assertEquals(1000, l.weights.preferenceSenPerLevel());
        assertEquals(8, l.weights.workloadPerHundredthHour());
        assertEquals(7200, l.weights.scaleCost(4800));
    }

    @Test
    void anUnmigratedDatabaseIsNeverReadAsRinggit() {
        // Before migration 010 the old seed values (1, 1, 1, 100) are in the table and there is
        // no LABOUR_COST row. They must NOT be taken as RM1 per hour etc.
        Map<String, BigDecimal> old = new HashMap<>();
        old.put("OVERTIME", new BigDecimal("1.00"));
        old.put("WORKLOAD", new BigDecimal("1.00"));
        old.put("PREFERENCE", new BigDecimal("1.00"));
        old.put("UNFILLED", new BigDecimal("100.00"));
        SolverWeightsDao.Loaded l = SolverWeightsDao.fromRules(old);
        assertNotNull(l.note);
        assertTrue(l.note.contains("migration 010"));
        assertEquals(SolverWeights.defaults().workloadPerHundredthHour(), l.weights.workloadPerHundredthHour());
        assertEquals(SolverWeights.defaults().unfilledSen(), l.weights.unfilledSen());
    }

    @Test
    void aMissingRuleFallsBackToItsDefaultWithANote() {
        Map<String, BigDecimal> m = migrated();
        m.remove("PREFERENCE");
        m.put("WORKLOAD", new BigDecimal("9.00"));
        SolverWeightsDao.Loaded l = SolverWeightsDao.fromRules(m);
        assertNotNull(l.note);
        assertTrue(l.note.contains("PREFERENCE"));
        assertEquals(200, l.weights.preferenceSenPerLevel());          // default
        assertEquals(9, l.weights.workloadPerHundredthHour());          // the stored one is kept
    }

    @Test
    void aFractionalWholeNumberWeightIsRefusedForThatWeightOnly() {
        Map<String, BigDecimal> m = migrated();
        m.put("WORKLOAD", new BigDecimal("2.50"));
        SolverWeightsDao.Loaded l = SolverWeightsDao.fromRules(m);
        assertNotNull(l.note);
        assertTrue(l.note.contains("WORKLOAD"));
        assertEquals(5, l.weights.workloadPerHundredthHour());          // default for that one
    }

    @Test
    void notAllowedValuesFallBackToAllDefaults() {
        Map<String, BigDecimal> m = migrated();
        m.put("PREFERENCE", new BigDecimal("-3.00"));
        SolverWeightsDao.Loaded l = SolverWeightsDao.fromRules(m);
        assertNotNull(l.note);
        assertEquals(SolverWeights.defaults().preferenceSenPerLevel(), l.weights.preferenceSenPerLevel());
    }
}
