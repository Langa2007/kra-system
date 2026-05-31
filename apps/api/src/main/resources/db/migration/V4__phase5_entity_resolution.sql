CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_taxpayers_legal_name_trgm ON taxpayers USING gin (legal_name gin_trgm_ops);
CREATE INDEX idx_taxpayers_trading_name_trgm ON taxpayers USING gin (trading_name gin_trgm_ops);
CREATE INDEX idx_invoices_supplier_pin ON invoices(supplier_pin);
CREATE INDEX idx_invoices_buyer_pin ON invoices(buyer_pin);
CREATE INDEX idx_customs_importer_pin ON customs_declarations(importer_pin);
CREATE INDEX idx_wht_payer_pin ON withholding_certificates(payer_pin);
CREATE INDEX idx_wht_payee_pin ON withholding_certificates(payee_pin);
CREATE INDEX idx_properties_owner_pin ON properties(owner_pin);
CREATE INDEX idx_payment_transactions_payer_pin ON payment_transactions(payer_pin);
CREATE INDEX idx_business_permits_activity_trgm ON business_permits USING gin (business_activity gin_trgm_ops);
