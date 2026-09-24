-- ============================================================================
-- V25  VAT registration status.
--
-- A shop below the registration threshold charges no VAT and must not issue a
-- document headed "Tax Invoice" - doing so claims a VAT registration it does
-- not hold. The distinction is the tenant's, not the tax rate's: a rate can
-- exist in the catalogue while the shop is still not registered to charge it.
--
-- Defaults to false: a shop that has not told us it is registered is treated as
-- not registered, which is the safe direction to be wrong in.
-- ============================================================================

ALTER TABLE tenants ADD COLUMN vat_registered boolean NOT NULL DEFAULT false;

-- A tenant that already recorded a tax identifier is almost certainly registered;
-- leaving those as false would silently drop VAT from their next invoice.
UPDATE tenants
   SET vat_registered = true
 WHERE tax_identifier IS NOT NULL
   AND btrim(tax_identifier) <> '';

COMMENT ON COLUMN tenants.vat_registered IS
    'Drives "Tax Invoice" vs "Invoice" and whether VAT may be charged at all.';
