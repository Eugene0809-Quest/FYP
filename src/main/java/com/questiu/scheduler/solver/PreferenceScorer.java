package com.questiu.scheduler.solver;

import com.questiu.scheduler.model.Preference;
import com.questiu.scheduler.model.RosterAssignment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reports how well a finished roster (automatic OR manual) respected the
 * stored shift preferences. It only looks at the assignments that were
 * actually made, so it works the same for CP-SAT output and hand-built
 * rosters, and needs no database or solver.
 *
 *  - preferredGranted: assignments where the employee had asked FOR that shift (level > 0)
 *  - avoidedImposed:   assignments where the employee had asked to AVOID that shift (level < 0)
 *  - violationScore:   the weighted version of avoidedImposed - the sum of |level|
 *                      over those assignments, so "strongly avoid" (-2) counts double
 *
 * A violationScore of 0 means nobody was given a shift they asked to avoid.
 */
public final class PreferenceScorer {

    private PreferenceScorer() {}

    public static class Result {
        public final int preferredGranted;
        public final int avoidedImposed;
        public final int violationScore;

        Result(int preferredGranted, int avoidedImposed, int violationScore) {
            this.preferredGranted = preferredGranted;
            this.avoidedImposed = avoidedImposed;
            this.violationScore = violationScore;
        }
    }

    public static Result score(List<RosterAssignment> assignments, List<Preference> preferences) {
        Map<String, Integer> levelByPair = new HashMap<>();
        for (Preference p : preferences) {
            levelByPair.put(p.getEmployeeId() + "_" + p.getShiftId(), p.getLevel());
        }

        int granted = 0;
        int imposed = 0;
        int score = 0;
        for (RosterAssignment a : assignments) {
            int level = levelByPair.getOrDefault(a.getEmployeeId() + "_" + a.getShiftId(), 0);
            if (level > 0) {
                granted++;
            } else if (level < 0) {
                imposed++;
                score += -level;
            }
        }
        return new Result(granted, imposed, score);
    }
}
