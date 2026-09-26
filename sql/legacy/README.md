# Legacy schema (superseded)

These two files (`shift_scheduling_schema.sql`, `shift_scheduling_seed_n5.sql`)
are the earlier `shift_scheduling` database design — `week_plan`-scoped
shifts/availability, `assignment`, `objective_weight` per week, etc.

**They are no longer what the Java code runs against.** As of the schema
realignment, `smartshift_schema.sql` and `seed_smartshift_n5.sql` (one
directory up, in `sql/`) are canonical. These legacy files are kept only
for provenance / in case anything in the FYP1 report references the old
table names and needs cross-checking.

Do not run these against a database you intend to use with the current
Java code — the DAOs query `shift_definition`, `employee`, `availability`,
`schedule`, etc. from the smartshift schema, not `shift`, `week_plan`,
`assignment` from this one.
