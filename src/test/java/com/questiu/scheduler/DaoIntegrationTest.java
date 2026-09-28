package com.questiu.scheduler;

import com.questiu.scheduler.dao.AvailabilityDao;
import com.questiu.scheduler.dao.EmployeeDao;
import com.questiu.scheduler.dao.ShiftDao;
import com.questiu.scheduler.model.Availability;
import com.questiu.scheduler.model.Employee;
import com.questiu.scheduler.model.Shift;

import java.util.List;

/**
 * Proves the JDBC DAO layer reads real rows out of the MySQL 'smartshift' DB.
 *
 * NOTE: employees and availability are live, admin-editable data (via the
 * Employees tab's registration screen and availability editor) - so their
 * counts grow over time and must NOT be hardcoded here (this test used to
 * assert exactly 5 employees / 33 availability rows, which broke the first
 * time a 6th employee was registered through the app, even though nothing
 * was actually broken). shift_definition (28 rows) has no admin UI to add
 * to it, so that count is a genuine structural invariant and is still
 * checked exactly - if it ever changes, something real did break.
 */
public class DaoIntegrationTest {
    public static void main(String[] args) throws Exception {
        List<Employee> employees = new EmployeeDao().findAllActive();
        List<Shift> shifts = new ShiftDao().findAll();
        List<Availability> availability = new AvailabilityDao().findAll();

        System.out.println("=== DAO Integration Test (real MySQL data) ===");
        System.out.println("Employees loaded: " + employees.size());
        employees.forEach(System.out::println);
        System.out.println("\nShifts loaded: " + shifts.size());
        shifts.stream().limit(5).forEach(System.out::println);
        System.out.println("... (" + (shifts.size() - 5) + " more)");
        System.out.println("\nAvailability rows loaded: " + availability.size());

        boolean pass = !employees.isEmpty() && shifts.size() == 28 && !availability.isEmpty();
        System.out.println("\nRESULT: " + (pass ? "PASS - DAO layer reads real DB data correctly" : "FAIL"));
        if (!pass) System.exit(1);
    }
}
