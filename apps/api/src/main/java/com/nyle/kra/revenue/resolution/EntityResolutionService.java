package com.nyle.kra.revenue.resolution;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.nyle.kra.revenue.audit.AuditService;
import com.nyle.kra.revenue.identity.AppUser;
import com.nyle.kra.revenue.identity.AppUserRepository;
import com.nyle.kra.revenue.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EntityResolutionService {

    private static final double AUTO_LINK_THRESHOLD = 0.55;

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final AppUserRepository appUserRepository;
    private final AuditService auditService;

    public EntityResolutionService(
            JdbcTemplate jdbcTemplate,
            NamedParameterJdbcTemplate namedJdbcTemplate,
            AppUserRepository appUserRepository,
            AuditService auditService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = namedJdbcTemplate;
        this.appUserRepository = appUserRepository;
        this.auditService = auditService;
    }

    @Transactional
    public EntityResolutionSummary run(AuthenticatedUser authenticatedUser, HttpServletRequest request) {
        int kraPinIdentifiers = update("""
                INSERT INTO taxpayer_identifiers (taxpayer_id, identifier_type, identifier_value, source, confidence_score)
                SELECT id, 'KRA_PIN', kra_pin, 'ENTITY_RESOLUTION', 100
                FROM taxpayers
                WHERE kra_pin IS NOT NULL AND kra_pin <> ''
                ON CONFLICT (identifier_type, identifier_value) DO NOTHING
                """);
        int registrationIdentifiers = update("""
                INSERT INTO taxpayer_identifiers (taxpayer_id, identifier_type, identifier_value, source, confidence_score)
                SELECT id, 'REGISTRATION_NUMBER', registration_number, 'ENTITY_RESOLUTION', 100
                FROM taxpayers
                WHERE registration_number IS NOT NULL AND registration_number <> ''
                ON CONFLICT (identifier_type, identifier_value) DO NOTHING
                """);
        int invoiceSupplierLinks = update("""
                UPDATE invoices i
                SET supplier_taxpayer_id = t.id
                FROM taxpayers t
                WHERE i.supplier_taxpayer_id IS NULL
                  AND i.supplier_pin IS NOT NULL
                  AND upper(i.supplier_pin) = upper(t.kra_pin)
                """);
        int invoiceBuyerLinks = update("""
                UPDATE invoices i
                SET buyer_taxpayer_id = t.id
                FROM taxpayers t
                WHERE i.buyer_taxpayer_id IS NULL
                  AND i.buyer_pin IS NOT NULL
                  AND upper(i.buyer_pin) = upper(t.kra_pin)
                """);
        int customsLinks = update("""
                UPDATE customs_declarations c
                SET taxpayer_id = t.id
                FROM taxpayers t
                WHERE c.taxpayer_id IS NULL
                  AND c.importer_pin IS NOT NULL
                  AND upper(c.importer_pin) = upper(t.kra_pin)
                """);
        int withholdingPayerLinks = update("""
                UPDATE withholding_certificates w
                SET payer_taxpayer_id = t.id
                FROM taxpayers t
                WHERE w.payer_taxpayer_id IS NULL
                  AND w.payer_pin IS NOT NULL
                  AND upper(w.payer_pin) = upper(t.kra_pin)
                """);
        int withholdingPayeeLinks = update("""
                UPDATE withholding_certificates w
                SET payee_taxpayer_id = t.id
                FROM taxpayers t
                WHERE w.payee_taxpayer_id IS NULL
                  AND w.payee_pin IS NOT NULL
                  AND upper(w.payee_pin) = upper(t.kra_pin)
                """);
        int propertyLinks = update("""
                UPDATE properties p
                SET owner_taxpayer_id = t.id
                FROM taxpayers t
                WHERE p.owner_taxpayer_id IS NULL
                  AND p.owner_pin IS NOT NULL
                  AND upper(p.owner_pin) = upper(t.kra_pin)
                """);
        int paymentLinks = update("""
                UPDATE payment_transactions p
                SET payer_taxpayer_id = t.id
                FROM taxpayers t
                WHERE p.payer_taxpayer_id IS NULL
                  AND p.payer_pin IS NOT NULL
                  AND upper(p.payer_pin) = upper(t.kra_pin)
                """);
        int permitLinks = update("""
                WITH ranked AS (
                    SELECT bp.id AS permit_id,
                           t.id AS taxpayer_id,
                           GREATEST(
                               similarity(lower(bp.business_activity), lower(t.legal_name)),
                               similarity(lower(bp.business_activity), lower(COALESCE(t.trading_name, '')))
                           ) AS confidence,
                           row_number() OVER (
                               PARTITION BY bp.id
                               ORDER BY GREATEST(
                                   similarity(lower(bp.business_activity), lower(t.legal_name)),
                                   similarity(lower(bp.business_activity), lower(COALESCE(t.trading_name, '')))
                               ) DESC
                           ) AS rank
                    FROM business_permits bp
                    JOIN taxpayers t ON bp.county IS NULL OR t.county IS NULL OR lower(bp.county) = lower(t.county)
                    WHERE bp.taxpayer_id IS NULL
                      AND bp.business_activity IS NOT NULL
                )
                UPDATE business_permits bp
                SET taxpayer_id = ranked.taxpayer_id
                FROM ranked
                WHERE bp.id = ranked.permit_id
                  AND ranked.rank = 1
                  AND ranked.confidence >= 0.55
                """);
        int duplicateRelationships = update("""
                WITH duplicate_pairs AS (
                    SELECT LEAST(a.id, b.id) AS source_id,
                           GREATEST(a.id, b.id) AS target_id,
                           CASE
                               WHEN a.registration_number IS NOT NULL
                                    AND a.registration_number <> ''
                                    AND lower(a.registration_number) = lower(b.registration_number)
                               THEN 1.00::numeric
                               ELSE GREATEST(
                                   similarity(lower(a.legal_name), lower(b.legal_name)),
                                   similarity(lower(COALESCE(a.trading_name, '')), lower(COALESCE(b.trading_name, '')))
                               )::numeric
                           END AS confidence
                    FROM taxpayers a
                    JOIN taxpayers b ON a.id < b.id
                    WHERE (
                        a.registration_number IS NOT NULL
                        AND a.registration_number <> ''
                        AND lower(a.registration_number) = lower(b.registration_number)
                    )
                    OR GREATEST(
                        similarity(lower(a.legal_name), lower(b.legal_name)),
                        similarity(lower(COALESCE(a.trading_name, '')), lower(COALESCE(b.trading_name, '')))
                    ) >= 0.90
                )
                INSERT INTO taxpayer_relationships (
                    source_taxpayer_id,
                    target_taxpayer_id,
                    relationship_type,
                    source,
                    confidence_score
                )
                SELECT source_id, target_id, 'POSSIBLE_DUPLICATE', 'ENTITY_RESOLUTION', confidence * 100
                FROM duplicate_pairs d
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM taxpayer_relationships r
                    WHERE r.source_taxpayer_id = d.source_id
                      AND r.target_taxpayer_id = d.target_id
                      AND r.relationship_type = 'POSSIBLE_DUPLICATE'
                )
                """);

        EntityResolutionSummary summary = new EntityResolutionSummary(
                kraPinIdentifiers,
                registrationIdentifiers,
                invoiceSupplierLinks,
                invoiceBuyerLinks,
                customsLinks,
                withholdingPayerLinks,
                withholdingPayeeLinks,
                propertyLinks,
                paymentLinks,
                permitLinks,
                duplicateRelationships
        );
        auditService.record(
                AuditService.ENTITY_RESOLUTION_RUN,
                actor(authenticatedUser),
                "entity_resolution",
                null,
                request,
                Map.of(
                        "totalLinksCreated", summary.totalLinksCreated(),
                        "duplicateRelationshipsCreated", summary.duplicateRelationshipsCreated()
                )
        );
        return summary;
    }

    public List<TaxpayerMatchCandidate> findMatchCandidates(String name, int limit) {
        return namedJdbcTemplate.query("""
                SELECT id,
                       kra_pin,
                       registration_number,
                       legal_name,
                       trading_name,
                       CASE
                           WHEN similarity(lower(:name), lower(legal_name)) >= similarity(lower(:name), lower(COALESCE(trading_name, '')))
                           THEN 'LEGAL_NAME'
                           ELSE 'TRADING_NAME'
                       END AS match_basis,
                       GREATEST(
                           similarity(lower(:name), lower(legal_name)),
                           similarity(lower(:name), lower(COALESCE(trading_name, '')))
                       ) AS confidence
                FROM taxpayers
                WHERE GREATEST(
                    similarity(lower(:name), lower(legal_name)),
                    similarity(lower(:name), lower(COALESCE(trading_name, '')))
                ) > 0
                ORDER BY confidence DESC, legal_name ASC
                LIMIT :limit
                """,
                new MapSqlParameterSource()
                        .addValue("name", name)
                        .addValue("limit", Math.max(1, Math.min(limit, 50))),
                (rs, rowNum) -> new TaxpayerMatchCandidate(
                        rs.getObject("id", UUID.class),
                        rs.getString("kra_pin"),
                        rs.getString("registration_number"),
                        rs.getString("legal_name"),
                        rs.getString("trading_name"),
                        rs.getString("match_basis"),
                        rs.getDouble("confidence"),
                        rs.getDouble("confidence") >= AUTO_LINK_THRESHOLD
                ));
    }

    public List<DuplicateTaxpayerCandidate> duplicateCandidates() {
        return jdbcTemplate.query("""
                SELECT a.id AS source_id,
                       b.id AS target_id,
                       a.legal_name AS source_name,
                       b.legal_name AS target_name,
                       CASE
                           WHEN a.registration_number IS NOT NULL
                                AND a.registration_number <> ''
                                AND lower(a.registration_number) = lower(b.registration_number)
                           THEN 'REGISTRATION_NUMBER'
                           ELSE 'FUZZY_NAME'
                       END AS match_basis,
                       CASE
                           WHEN a.registration_number IS NOT NULL
                                AND a.registration_number <> ''
                                AND lower(a.registration_number) = lower(b.registration_number)
                           THEN 1.00
                           ELSE GREATEST(
                               similarity(lower(a.legal_name), lower(b.legal_name)),
                               similarity(lower(COALESCE(a.trading_name, '')), lower(COALESCE(b.trading_name, '')))
                           )
                       END AS confidence
                FROM taxpayers a
                JOIN taxpayers b ON a.id < b.id
                WHERE (
                    a.registration_number IS NOT NULL
                    AND a.registration_number <> ''
                    AND lower(a.registration_number) = lower(b.registration_number)
                )
                OR GREATEST(
                    similarity(lower(a.legal_name), lower(b.legal_name)),
                    similarity(lower(COALESCE(a.trading_name, '')), lower(COALESCE(b.trading_name, '')))
                ) >= 0.90
                ORDER BY confidence DESC, source_name ASC
                """,
                (rs, rowNum) -> new DuplicateTaxpayerCandidate(
                        rs.getObject("source_id", UUID.class),
                        rs.getObject("target_id", UUID.class),
                        rs.getString("source_name"),
                        rs.getString("target_name"),
                        rs.getString("match_basis"),
                        rs.getDouble("confidence")
                ));
    }

    private int update(String sql) {
        return jdbcTemplate.update(sql);
    }

    private java.util.Optional<AppUser> actor(AuthenticatedUser authenticatedUser) {
        if (authenticatedUser == null) {
            return java.util.Optional.empty();
        }
        return appUserRepository.findById(authenticatedUser.getUserId());
    }
}
