package com.nyle.kra.revenue.taxpayer;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxpayerRepository extends JpaRepository<Taxpayer, UUID> {
}
