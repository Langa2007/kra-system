package com.nyle.kra.revenue.taxpayer;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "taxpayers")
public class Taxpayer {

    @Id
    private UUID id;

    @Column(name = "kra_pin", unique = true)
    private String kraPin;

    @Column(name = "taxpayer_type", nullable = false)
    private String taxpayerType;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(name = "trading_name")
    private String tradingName;

    @Column(name = "registration_number")
    private String registrationNumber;

    @Column(name = "sector_code")
    private String sectorCode;

    @Column(name = "sector_name")
    private String sectorName;

    @Column(name = "tax_office")
    private String taxOffice;

    private String county;

    @Column(nullable = false)
    private String status;

    @Column(name = "registered_at")
    private LocalDate registeredAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Taxpayer() {
    }

    public Taxpayer(String kraPin, String taxpayerType, String legalName, String status) {
        this.id = UUID.randomUUID();
        this.kraPin = kraPin;
        this.taxpayerType = taxpayerType;
        this.legalName = legalName;
        this.status = status;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getKraPin() {
        return kraPin;
    }
}
