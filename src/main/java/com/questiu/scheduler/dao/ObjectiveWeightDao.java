package com.questiu.scheduler.dao;

import com.questiu.scheduler.solver.SolverWeights;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Records, for one saved roster, the objective weights that were used to generate it
 * (objective_weight, one row per schedule, migration 010). Only automatic (CP-SAT)
 * rosters have weights; a roster built by hand has none.
 */
public class ObjectiveWeightDao {

    public void save(int scheduleId, SolverWeights w) throws SQLException {
        String sql = "INSERT INTO objective_weight (schedule_id, lambda_overtime, lambda_workload, "
                + "lambda_preference, lambda_unfilled, lambda_cost) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, scheduleId);
            ps.setBigDecimal(2, rm(w.getOvertimeRmPerHour()));
            ps.setBigDecimal(3, rm(w.getWorkloadRmPerHour()));
            ps.setBigDecimal(4, rm(w.getPreferenceRmPerLevel()));
            ps.setBigDecimal(5, rm(w.getUnfilledRm()));
            ps.setBigDecimal(6, rm(w.getCostMultiplier()));
            ps.executeUpdate();
        }
    }

    private static BigDecimal rm(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
