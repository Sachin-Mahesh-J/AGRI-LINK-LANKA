/**
 * Wipe all Firestore data for AgriScout, then load fresh demo seed data.
 *
 * Usage:
 *   cd admin-dashboard
 *   npm run seed:reset
 *
 * Requires service-account.json or gcloud application-default credentials
 * (same setup as npm run seed — see seed-firestore.mjs header).
 */

process.env.SEED_RESET = 'true'
process.env.SEED_CONFIRM = 'yes'

await import('./seed-firestore.mjs')
