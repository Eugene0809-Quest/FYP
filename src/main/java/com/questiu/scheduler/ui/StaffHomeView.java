package com.questiu.scheduler.ui;

import com.questiu.scheduler.dao.EmployeeDao;
import com.questiu.scheduler.dao.JobPositionDao;
import com.questiu.scheduler.dao.RoleDao;
import com.questiu.scheduler.model.Company;
import com.questiu.scheduler.model.Employee;
import com.questiu.scheduler.model.Role;
import com.questiu.scheduler.model.User;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The screen a STAFF login lands on (FYP2, lecturer feedback: "login then
 * lead to staff or admin"). A staff member sees only their own record - none
 * of the admin tabs (roster generation, all employees, public holidays).
 *
 * Tabs: "My Profile", "My Schedule" (own shifts from published rosters) and
 * "My Leave" (request leave, see its status).
 */
public class StaffHomeView {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public static BorderPane build(User user, Company company, Runnable onLogout) {
        Label companyLabel = new Label(company != null ? company.getCompanyName() : "SmartShift");
        companyLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: white;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label sessionLabel = new Label("Logged in as: " + user.getFullName() + " (" + user.getRole() + ")");
        Button logoutButton = new Button("Log Out");
        logoutButton.setOnAction(e -> onLogout.run());
        Button changePasswordButton = new Button("Change Password");
        changePasswordButton.setOnAction(e ->
                ChangePasswordDialog.show(changePasswordButton.getScene().getWindow(), user));

        HBox topBar = new HBox(15, companyLabel, spacer, sessionLabel, changePasswordButton, logoutButton);
        topBar.setPadding(new Insets(10, 20, 10, 20));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("topbar");

        TabPane tabs = new TabPane();
        Tab profileTab = new Tab("My Profile", buildProfile(user));
        profileTab.setClosable(false);
        tabs.getTabs().add(profileTab);
        Tab scheduleTab = new Tab("My Schedule", MyScheduleView.build(user));
        scheduleTab.setClosable(false);
        tabs.getTabs().add(scheduleTab);
        Tab leaveTab = new Tab("My Leave", StaffLeavePane.build(user));
        leaveTab.setClosable(false);
        tabs.getTabs().add(leaveTab);

        BorderPane root = new BorderPane();
        root.setTop(topBar);
        root.setCenter(tabs);
        return root;
    }

    private static Node buildProfile(User user) {
        VBox box = new VBox(14);
        box.setPadding(new Insets(20));

        Label heading = new Label("My Profile");
        heading.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        Label status = new Label();
        status.setWrapText(true);
        box.getChildren().add(heading);

        if (user.getEmployeeId() == null) {
            status.setText("This login is not linked to an employee record. Please ask an administrator.");
            box.getChildren().add(status);
            return box;
        }

        try {
            Employee e = new EmployeeDao().findById(user.getEmployeeId());
            if (e == null) {
                status.setText("Your employee record could not be found. Please ask an administrator.");
                box.getChildren().add(status);
                return box;
            }
            Map<Integer, String> roleNames = new RoleDao().findAll().stream()
                    .collect(Collectors.toMap(Role::getRoleId, Role::getRoleName));
            Map<Integer, String> positionNames = new JobPositionDao().findNamesById();

            GridPane grid = new GridPane();
            grid.setHgap(20);
            grid.setVgap(10);
            int r = 0;
            addRow(grid, r++, "Full name", e.getFullName());
            addRow(grid, r++, "Department", roleNames.get(e.getRoleId()));
            addRow(grid, r++, "Job position",
                    e.getJobPositionId() == null ? null : positionNames.get(e.getJobPositionId()));
            addRow(grid, r++, "Employment type", e.getEmploymentType().name());
            addRow(grid, r++, "Date joined", e.getDateJoined() == null ? null : DMY.format(e.getDateJoined()));
            addRow(grid, r++, "IC number", e.getIcNumber());
            addRow(grid, r++, "EPF number", e.getEpfNumber());
            addRow(grid, r++, "SOCSO number", e.getSocsoNumber());
            addRow(grid, r++, "EIS number", e.getEisNumber());
            addRow(grid, r++, "Bank account", e.getBankAccountNumber());
            addRow(grid, r++, "Hourly rate (RM)", e.getHourlyRate().toPlainString());
            addRow(grid, r++, "Max hours per week", e.getMaxWeeklyHours().toPlainString());
            box.getChildren().add(grid);
        } catch (Exception ex) {
            status.setText("Could not load your profile: " + ex.getMessage());
            box.getChildren().add(status);
        }
        return box;
    }

    private static void addRow(GridPane grid, int row, String label, String value) {
        Label name = new Label(label + ":");
        name.setStyle("-fx-font-weight: bold;");
        Label shown = new Label(value == null || value.isBlank() ? "-" : value);
        grid.addRow(row, name, shown);
    }
}
