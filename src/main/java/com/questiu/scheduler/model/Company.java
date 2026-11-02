package com.questiu.scheduler.model;

import java.time.LocalTime;

/**
 * The single company this installation belongs to (company table, migration
 * 007). The system is scoped to one outlet, so there is exactly one row.
 * Used for the window title, the top bar and, later, the payslip header.
 */
public class Company {
    private final String companyName;
    private final String registrationNo;
    private final String businessType;
    private final String address;
    private final String contactNumber;
    private final String email;
    private final LocalTime openingTime;
    private final LocalTime closingTime;
    private final String description;

    public Company(String companyName, String registrationNo, String businessType, String address,
                   String contactNumber, String email, LocalTime openingTime, LocalTime closingTime,
                   String description) {
        this.companyName = companyName;
        this.registrationNo = registrationNo;
        this.businessType = businessType;
        this.address = address;
        this.contactNumber = contactNumber;
        this.email = email;
        this.openingTime = openingTime;
        this.closingTime = closingTime;
        this.description = description;
    }

    public String getCompanyName() { return companyName; }
    public String getRegistrationNo() { return registrationNo; }
    public String getBusinessType() { return businessType; }
    public String getAddress() { return address; }
    public String getContactNumber() { return contactNumber; }
    public String getEmail() { return email; }
    public LocalTime getOpeningTime() { return openingTime; }
    public LocalTime getClosingTime() { return closingTime; }
    public String getDescription() { return description; }
}
