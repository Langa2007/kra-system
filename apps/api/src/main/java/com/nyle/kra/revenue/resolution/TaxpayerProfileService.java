package com.nyle.kra.revenue.resolution;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TaxpayerProfileService {

    private final JdbcTemplate jdbcTemplate;

    public TaxpayerProfileService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public TaxpayerProfileResponse profile(UUID taxpayerId) {
        TaxpayerProfileRow taxpayer = jdbcTemplate.query("""
                SELECT id,
                       kra_pin,
                       taxpayer_type,
                       legal_name,
                       trading_name,
                       registration_number,
                       sector_name,
                       county
                FROM taxpayers
                WHERE id = ?
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            return new TaxpayerProfileRow(
                    rs.getObject("id", UUID.class),
                    rs.getString("kra_pin"),
                    rs.getString("taxpayer_type"),
                    rs.getString("legal_name"),
                    rs.getString("trading_name"),
                    rs.getString("registration_number"),
                    rs.getString("sector_name"),
                    rs.getString("county")
            );
        }, taxpayerId);

        if (taxpayer == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Taxpayer not found");
        }

        return new TaxpayerProfileResponse(
                taxpayer.id(),
                taxpayer.kraPin(),
                taxpayer.taxpayerType(),
                taxpayer.legalName(),
                taxpayer.tradingName(),
                taxpayer.registrationNumber(),
                taxpayer.sectorName(),
                taxpayer.county(),
                identifiers(taxpayerId),
                counts(taxpayerId),
                totals(taxpayerId),
                relationships(taxpayerId)
        );
    }

    private List<TaxpayerProfileResponse.Identifier> identifiers(UUID taxpayerId) {
        return jdbcTemplate.query("""
                SELECT identifier_type, identifier_value, source, confidence_score
                FROM taxpayer_identifiers
                WHERE taxpayer_id = ?
                ORDER BY identifier_type, identifier_value
                """,
                (rs, rowNum) -> new TaxpayerProfileResponse.Identifier(
                        rs.getString("identifier_type"),
                        rs.getString("identifier_value"),
                        rs.getString("source"),
                        rs.getBigDecimal("confidence_score")
                ),
                taxpayerId);
    }

    private TaxpayerProfileResponse.Counts counts(UUID taxpayerId) {
        return jdbcTemplate.queryForObject("""
                SELECT
                    (SELECT count(*) FROM invoices WHERE supplier_taxpayer_id = ?) AS invoices_as_supplier,
                    (SELECT count(*) FROM invoices WHERE buyer_taxpayer_id = ?) AS invoices_as_buyer,
                    (SELECT count(*) FROM tax_returns WHERE taxpayer_id = ?) AS tax_returns,
                    (SELECT count(*) FROM customs_declarations WHERE taxpayer_id = ?) AS customs_declarations,
                    (SELECT count(*) FROM withholding_certificates WHERE payer_taxpayer_id = ?) AS withholding_as_payer,
                    (SELECT count(*) FROM withholding_certificates WHERE payee_taxpayer_id = ?) AS withholding_as_payee,
                    (SELECT count(*) FROM business_permits WHERE taxpayer_id = ?) AS business_permits,
                    (SELECT count(*) FROM properties WHERE owner_taxpayer_id = ?) AS properties,
                    (SELECT count(*) FROM payment_transactions WHERE payer_taxpayer_id = ?) AS payment_transactions
                """,
                (rs, rowNum) -> new TaxpayerProfileResponse.Counts(
                        rs.getInt("invoices_as_supplier"),
                        rs.getInt("invoices_as_buyer"),
                        rs.getInt("tax_returns"),
                        rs.getInt("customs_declarations"),
                        rs.getInt("withholding_as_payer"),
                        rs.getInt("withholding_as_payee"),
                        rs.getInt("business_permits"),
                        rs.getInt("properties"),
                        rs.getInt("payment_transactions")
                ),
                taxpayerId,
                taxpayerId,
                taxpayerId,
                taxpayerId,
                taxpayerId,
                taxpayerId,
                taxpayerId,
                taxpayerId,
                taxpayerId);
    }

    private TaxpayerProfileResponse.MoneyTotals totals(UUID taxpayerId) {
        return jdbcTemplate.queryForObject("""
                SELECT
                    COALESCE((SELECT sum(total_amount) FROM invoices WHERE supplier_taxpayer_id = ?), 0) AS supplier_invoice_total,
                    COALESCE((SELECT sum(total_amount) FROM invoices WHERE buyer_taxpayer_id = ?), 0) AS buyer_invoice_total,
                    COALESCE((SELECT sum(declared_sales) FROM tax_returns WHERE taxpayer_id = ?), 0) AS declared_sales,
                    COALESCE((SELECT sum(customs_value) FROM customs_declarations WHERE taxpayer_id = ?), 0) AS customs_value,
                    COALESCE((SELECT sum(amount) FROM payment_transactions WHERE payer_taxpayer_id = ?), 0) AS payments
                """,
                (rs, rowNum) -> new TaxpayerProfileResponse.MoneyTotals(
                        value(rs.getBigDecimal("supplier_invoice_total")),
                        value(rs.getBigDecimal("buyer_invoice_total")),
                        value(rs.getBigDecimal("declared_sales")),
                        value(rs.getBigDecimal("customs_value")),
                        value(rs.getBigDecimal("payments"))
                ),
                taxpayerId,
                taxpayerId,
                taxpayerId,
                taxpayerId,
                taxpayerId);
    }

    private List<TaxpayerProfileResponse.Relationship> relationships(UUID taxpayerId) {
        return jdbcTemplate.query("""
                SELECT target_taxpayer_id, related_person_name, relationship_type, source, confidence_score
                FROM taxpayer_relationships
                WHERE source_taxpayer_id = ?
                UNION ALL
                SELECT source_taxpayer_id, related_person_name, relationship_type, source, confidence_score
                FROM taxpayer_relationships
                WHERE target_taxpayer_id = ?
                ORDER BY confidence_score DESC
                """,
                (rs, rowNum) -> new TaxpayerProfileResponse.Relationship(
                        rs.getObject("target_taxpayer_id", UUID.class),
                        rs.getString("related_person_name"),
                        rs.getString("relationship_type"),
                        rs.getString("source"),
                        rs.getBigDecimal("confidence_score")
                ),
                taxpayerId,
                taxpayerId);
    }

    private BigDecimal value(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private record TaxpayerProfileRow(
            UUID id,
            String kraPin,
            String taxpayerType,
            String legalName,
            String tradingName,
            String registrationNumber,
            String sectorName,
            String county
    ) {
    }
}
