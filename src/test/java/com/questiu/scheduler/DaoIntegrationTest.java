package com.questiu.scheduler;

import com.questiu.scheduler.dao.AvailabilityDao;
import com.questiu.scheduler.dao.EmployeeDao;
import com.questiu.scheduler.dao.ShiftDao;
import com.questiu.scheduler.model.Availability;
import com.questiu.scheduler.model.Employee;
import com.questiu.scheduler.model.Shift;

import java.util.List;

/** Proves the JDBC DAO layer reads real rows out of the MySQL 'shift_scheduling' DB. */
public class DaoIntegrationTest {
    public static void main(String[] args) throws Exception {
        List<Employee> employees = new EmployeeDao().findAllActive();
        List<Shift> shifts = new ShiftDao().findByWeekPlan(1);
        List<Availability> availability = new AvailabilityDao().findByWeekPlan(1);

        System.out.println("=== DAO Integration Test (real MySQL data) ===");
        System.out.println("Employees loaded: " + employees.size());
        employees.forEach(System.out::println);
        System.out.println("\nShifts loaded: " + shifts.size());
        shifts.stream().limit(5).forEach(System.out::println);
        System.out.println("... (" + (shifts.size() - 5) + " more)");
        System.out.println("\nAvailability rows loaded: " + availability.size());

        boolean pass = employees.size() == 5 && shifts.size() == 28 && availability.size() == 33;
        System.out.println("\nRESULT: " + (pass ? "PASS - DAO layer reads real DB data correctly" : "FAIL"));
        if (!pass) System.exit(1);
    }
}
