package com.questiu.scheduler;

import com.questiu.scheduler.dao.AvailabilityDao;
import com.questiu.scheduler.dao.EmployeeDao;
import com.questiu.scheduler.dao.PublicHolidayDao;
import com.questiu.scheduler.dao.ShiftDao;
import com.questiu.scheduler.model.*;
import com.questiu.scheduler.payroll.PayrollCalculator;
import com.questiu.scheduler.solver.SchedulingEngine;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * End-to-end pipeline runner: pulls the n=5 week plan from MySQL, solves the
 * roster with CP-SAT, computes payroll from the solved roster (including the
 * item #4 public holiday premium), and prints both to the console. This is
 * the FYP1 "early prototype" run referenced in Section 1.5 - the single most
 * important piece of evidence that the scheduling + payroll pipeline
 * actually works end-to-end, not just on paper.
 *
 * Run with: mvn compile exec:java -Dexec.mainClass=com.questiu.scheduler.Main
 * (after `mysql -u root < sql/smartshift_schema.sql` and
 *  `mysql -u root < sql/seed_smartshift_n5.sql`)
 */
public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println("=== Loading data from MySQL (smartshift) ===");
        List<Employee> employees = new EmployeeDao().findAllActive();
        List<Shift> shifts = new ShiftDao().findAll();
        List<Availability> availability = new AvailabilityDao().findAll();
        Set<LocalDate> publicHolidays = new PublicHolidayDao().findAllDates();
        System.out.printf("Loaded %d employees, %d shifts, %d availability rows, %d public holidays on file%n%n",
                employees.size(), shifts.size(), availability.size(), publicHolidays.size());

        // This console pipeline has no persisted "schedule" row yet (that's a
        // separate known gap - see README's "Known simplifications"), so the
        // week being rostered here is just "the Monday of the current
        // calendar week", for demo purposes only.
        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        long holidaysThisWeek = publicHolidays.stream()
                .filter(d -> !d.isBefore(weekStart) && d.isBefore(weekStart.plusDays(7)))
                .count();
        System.out.println("Rostering week starting: " + weekStart + " (Monday)");
        System.out.println("Public holidays falling in this week: " + holidaysThisWeek);
        System.out.println();

        System.out.println("=== Solving with CP-SAT ===");
        SchedulingEngine engine = new SchedulingEngine();
        SchedulingEngine.SolveResult result = engine.solve(employees, shifts, availability, 10.0);

        System.out.println("Status: " + result.status);
        System.out.println("Solve time: " + result.solveTimeMillis + " ms");
        System.out.println("Total unfilled slots: " + result.totalUnfilled);
        System.out.println();

        if (!result.isFeasible()) {
            System.out.println("No feasible schedule found - check availability/coverage data.");
            return;
        }

        System.out.println("=== Payroll (Section 3.5 formula, incl. public holiday premium) ===");
        Map<Integer, Shift> shiftsById = shifts.stream()
                .collect(Collectors.toMap(Shift::getShiftId, s -> s));
        PayrollCalculator calc = new PayrollCalculator();
        List<PayrollRecord> payroll = calc.calculate(employees, shiftsById, result.assignments, weekStart, publicHolidays);
        for (PayrollRecord record : payroll) {
            System.out.println(record);
        }

        double totalPayroll = payroll.stream().mapToDouble(p -> p.getTotalPay().doubleValue()).sum();
        System.out.printf("%nEstimated total weekly payroll: RM%.2f%n", totalPayroll);
    }
}
