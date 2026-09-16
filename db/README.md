# Database

Migrations live on the API classpath at
[`backend/app/src/main/resources/db/migration`](../backend/app/src/main/resources/db/migration)
so they ship inside the boot jar and run identically in dev, CI and production.
Flyway executes them automatically on startup.

## Migration order

| Version | File                             | Contents                                                                                  |
| ------- | -------------------------------- | ----------------------------------------------------------------------------------------- |
| V1      | `V1__extensions_and_helpers.sql` | Extensions, `uuid_generate_v7()`, `current_tenant_id()`, the least-privilege runtime role |
| V2      | `V2__tenancy.sql`                | Tenants, outlets, settings, document numbering, tax rates, lookup lists                   |
| V3      | `V3__subscription.sql`           | Plans, entitlements, subscriptions, invoices, gateway events                              |
| V4      | `V4__identity.sql`               | Users, roles, permissions, refresh tokens, devices                                        |
| V5      | `V5__crm.sql`                    | Customers                                                                                 |
| V6      | `V6__catalog.sql`                | Items, suppliers, serials, GRNs, the stock ledger                                         |
| V7      | `V7__sales.sql`                  | Carts, bills, payments, credit notes, refunds                                             |
| V8      | `V8__repairs.sql`                | Repair orders and status history                                                          |
| V9      | `V9__wholesale.sql`              | Wholesale invoices and the customer credit ledger                                         |
| V10     | `V10__quotations.sql`            | Quotations                                                                                |
| V11     | `V11__audit.sql`                 | Unified audit trail and the outbox                                                        |
| V12     | `V12__row_level_security.sql`    | RLS policies and runtime grants                                                           |
| V13     | `V13__seed_reference_data.sql`   | Permission catalogue, system roles, plans                                                 |

## Two database roles, on purpose

Migrations run as the **owner** (`DB_OWNER_USER`). The API runs as
`DB_APP_USER`, which deliberately does _not_ own the tables.

This matters: in PostgreSQL a table's owner implicitly bypasses Row-Level
Security. If the API connected as the owner, every tenant-isolation policy in V12
would be inert and one missing `WHERE` clause would leak another shop's sales.

Never point `DB_APP_USER` at a superuser or the owner.

## Writing a new migration

1. Add `V{n}__snake_case_name.sql`. Never edit a migration that has shipped.
2. Give every tenant-scoped table a `tenant_id uuid NOT NULL REFERENCES tenants (id)`.
3. Enable RLS and add the `tenant_isolation` policy. The guard rail at the end of
   V12 is re-evaluated by `RowLevelSecurityTest`, so a table with `tenant_id` and
   no policy fails the build.
4. Money is `numeric(14, 2)`; quantities are `numeric(14, 3)`; identifiers are
   `uuid` defaulting to `uuid_generate_v7()`.

## Useful commands

```bash
# Bring up Postgres, Redis and MinIO
pnpm infra:up

# Wipe and recreate from scratch
pnpm infra:reset

# Inspect the applied history
docker exec -it possaas-postgres psql -U possaas -d possaas \
  -c "select version, description, success from flyway_schema_history order by installed_rank"

# Prove RLS is active for the runtime role
docker exec -it possaas-postgres psql -U possaas_app -d possaas \
  -c "select count(*) from items"   -- 0 rows: no tenant context set
```
