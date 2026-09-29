package com.questiu.scheduler.dao;

import com.questiu.scheduler.model.PayrollRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * Persists computed payroll (Section 3.5, extended for item #4 holiday pay
 * and item #5 EPF/SOCSO/EIS) into payroll_record, for a given schedule_id
 * from ScheduleDao.create(). total_pay is NOT inserted - it's a MySQL
 * GENERATED ALWAYS column (regular_pay + overtime_pay + holiday_pay),
 * computed by the database itself.
 */
public class PayrollRecordDao {

    public void saveAll(int scheduleId, List<PayrollRecord> payroll) throws SQLException {
        String sql = "INSERT INTO payroll_record (" +
                "schedule_id, employee_id, regular_hours, overtime_hours, holiday_hours, " +
                "regular_pay, overtime_pay, holiday_pay, " +
                "employee_epf, employer_epf, employee_socso, employer_socso, " +
                "employee_eis, employer_eis, net_pay, employer_total_cost" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (PayrollRecord p : payroll) {
                PayrollRecord.StatutoryBreakdown st = p.getStatutory();
                ps.setInt(1, scheduleId);
                ps.setInt(2, p.getEmployeeId());
                ps.setBigDecimal(3, java.math.BigDecimal.valueOf(p.getRegularHours()).setScale(2, java.math.RoundingMode.HALF_UP));
                ps.setBigDecimal(4, java.math.BigDecimal.valueOf(p.getOvertimeHours()).setScale(2, java.math.RoundingMode.HALF_UP));
                ps.setBigDecimal(5, java.math.BigDecimal.valueOf(p.getHolidayHours()).setScale(2, java.math.RoundingMode.HALF_UP));
                ps.setBigDecimal(6, p.getRegularPay());
                ps.setBigDecimal(7, p.getOvertimePay());
                ps.setBigDecimal(8, p.getHolidayPay());
                ps.setBigDecimal(9, st.getEmployeeEpf());
                ps.setBigDecimal(10, st.getEmployerEpf());
                ps.setBigDecimal(11, st.getEmployeeSocso());
                ps.setBigDecimal(12, st.getEmployerSocso());
                ps.setBigDecimal(13, st.getEmployeeEis());
                ps.setBigDecimal(14, st.getEmployerEis());
                ps.setBigDecimal(15, st.getNetPay());
                ps.setBigDecimal(16, st.getEmployerTotalCost());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
