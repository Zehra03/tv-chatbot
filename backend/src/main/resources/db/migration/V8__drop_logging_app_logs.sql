-- V8__drop_logging_app_logs.sql
-- Logs are not kept in the database.
--
-- V1 created logging.app_logs (plus its indexes) as the "Log DB" of the planned Log Module. That
-- design has since been ruled out: logs must not live in the database. The table is being dropped
-- rather than left in place, because dead schema does not stay dead — it reads as the sanctioned
-- destination and invites the next person to start writing there.
--
-- Nothing is lost. No entity, repository or query has ever referenced this table: the only log
-- sinks in the codebase are SLF4J (Slf4jAuditLogModule and plain loggers) and LogModuleClient's
-- HTTP POST. It has never held a row.
--
-- The whole schema goes with it: app_logs was its only object, so an empty `logging` schema would
-- be the same invitation in smaller form. Where logs ultimately land (structured stdout, an
-- external collector, ...) is still open — but it is not here.

DROP TABLE IF EXISTS logging.app_logs;

DROP SCHEMA IF EXISTS logging;
