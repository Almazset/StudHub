-- Read-only evidence for a QUIESCENT source and its restored copy.
-- Only counts and hashes leave PostgreSQL; no user rows or secrets are printed.
SET timezone = 'UTC';
SELECT format(
  $query$SELECT %L || '|' || count(*) || '|' || md5(COALESCE(string_agg(row_to_json(t)::text, '' ORDER BY row_to_json(t)::text COLLATE "C"), '')) FROM %I.%I t;$query$,
  'table:' || schemaname || '.' || tablename, schemaname, tablename)
FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename
\gexec
SELECT format('SELECT %L || ''|'' || last_value || ''|'' || is_called FROM %I.%I;',
  'sequence:' || schemaname || '.' || sequencename, schemaname, sequencename)
FROM pg_sequences WHERE schemaname = 'public' ORDER BY sequencename
\gexec
-- PostgreSQL can reparse CHECK array casts into equivalent expression trees on restore.
-- Compare CHECK identity/columns/validation; other constraints also compare definitions.
SELECT 'constraint:' || conrelid::regclass::text || ':' || conname || '|' || convalidated || '|' || CASE WHEN contype = 'c' THEN md5(conkey::text) ELSE md5(pg_get_constraintdef(oid)) END
FROM pg_constraint WHERE connamespace = 'public'::regnamespace ORDER BY conrelid::regclass::text, conname;
SELECT 'index:' || tablename || ':' || indexname || '|' || md5(indexdef)
FROM pg_indexes WHERE schemaname = 'public' ORDER BY tablename, indexname;

SELECT 'column:' || table_name || ':' || column_name || '|' || md5(concat_ws('|', ordinal_position, data_type, udt_name, character_maximum_length, is_nullable, column_default, is_identity, identity_generation))
FROM information_schema.columns WHERE table_schema = 'public' ORDER BY table_name, ordinal_position;
