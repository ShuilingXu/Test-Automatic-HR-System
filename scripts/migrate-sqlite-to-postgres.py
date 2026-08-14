#!/usr/bin/env python3
"""One-time, transactional SQLite to PostgreSQL data migration for Auto HR."""

import argparse
import os
import sqlite3

try:
    import psycopg
except ImportError as error:
    raise SystemExit("Install psycopg[binary] before running this migration.") from error


TABLES = [
    "sys_user", "hr_department", "hr_employee", "hr_integration_binding",
    "recruitment_job", "recruitment_candidate", "recruitment_resume_file",
    "interview_batch", "interview_question", "interview_candidate", "interview_submission",
    "sys_audit_log", "interview_knowledge_base", "interview_knowledge_item",
    "interview_job_knowledge_weight", "interview_llm_config", "interview_process_template",
    "interview_process_template_stage", "interview_process", "interview_process_stage",
    "interview_ai_record", "interview_video_session",
]

TYPE_COLUMNS = {
    "smallint", "integer", "bigint", "numeric", "decimal", "real", "double precision",
    "boolean", "date", "time without time zone", "time with time zone",
    "timestamp without time zone", "timestamp with time zone",
}


def quote(identifier):
    return '"' + identifier.replace('"', '""') + '"'


def target_table_exists(cursor, table):
    cursor.execute(
        "SELECT EXISTS (SELECT 1 FROM information_schema.tables "
        "WHERE table_schema = 'public' AND table_name = %s)",
        (table,),
    )
    return cursor.fetchone()[0]


def require_empty_target(cursor):
    nonempty_tables = []
    for table in TABLES:
        if not target_table_exists(cursor, table):
            continue
        cursor.execute(f"SELECT COUNT(*) FROM {quote(table)}")
        count = cursor.fetchone()[0]
        if count:
            nonempty_tables.append(f"{table} ({count})")
    if nonempty_tables:
        joined = ", ".join(nonempty_tables)
        raise SystemExit(
            "Target PostgreSQL contains data in: " + joined
            + ". Re-run only after verification with --force-overwrite."
        )


def target_column_types(cursor, table):
    cursor.execute(
        "SELECT column_name, data_type FROM information_schema.columns "
        "WHERE table_schema = 'public' AND table_name = %s",
        (table,),
    )
    return dict(cursor.fetchall())


def normalize_value(value, target_type):
    if value == "" and target_type in TYPE_COLUMNS:
        return None
    return value


def reset_sequences(cursor):
    for table in TABLES:
        if not target_table_exists(cursor, table):
            continue
        cursor.execute(
            "SELECT column_name FROM information_schema.columns "
            "WHERE table_schema = 'public' AND table_name = %s "
            "AND (column_default LIKE 'nextval%%' OR is_identity = 'YES')",
            (table,),
        )
        for (column,) in cursor.fetchall():
            cursor.execute("SELECT pg_get_serial_sequence(%s, %s)", (table, column))
            sequence = cursor.fetchone()[0]
            if sequence is None:
                continue
            cursor.execute(f"SELECT MAX({quote(column)}) FROM {quote(table)}")
            maximum = cursor.fetchone()[0]
            cursor.execute("SELECT setval(%s, %s, %s)", (sequence, maximum or 1, maximum is not None))


def migrate(sqlite_path, postgres_dsn, force_overwrite=False, dry_run=False):
    source = sqlite3.connect(sqlite_path)
    source.row_factory = sqlite3.Row
    try:
        with psycopg.connect(postgres_dsn) as target:
            with target.cursor() as cursor:
                if not dry_run and not force_overwrite:
                    require_empty_target(cursor)
                if not dry_run:
                    cursor.execute("SET CONSTRAINTS ALL DEFERRED")
                for table in TABLES:
                    columns = [row[1] for row in source.execute(f"PRAGMA table_info({quote(table)})")]
                    if not columns:
                        print(f"{table}: skipped (not present in SQLite source)")
                        continue
                    if not target_table_exists(cursor, table):
                        raise SystemExit(f"Target PostgreSQL table is missing: {table}")
                    target_types = target_column_types(cursor, table)
                    missing_columns = [column for column in columns if column not in target_types]
                    if missing_columns:
                        raise SystemExit(f"Target PostgreSQL table {table} is missing columns: {', '.join(missing_columns)}")
                    rows = source.execute(f"SELECT * FROM {quote(table)}").fetchall()
                    print(f"{table}: {len(rows)} rows")
                    if dry_run:
                        continue
                    cursor.execute(f"TRUNCATE TABLE {quote(table)} CASCADE")
                    if rows:
                        column_list = ", ".join(quote(column) for column in columns)
                        placeholders = ", ".join(["%s"] * len(columns))
                        cursor.executemany(
                            f"INSERT INTO {quote(table)} ({column_list}) VALUES ({placeholders})",
                            [
                                tuple(normalize_value(row[column], target_types[column]) for column in columns)
                                for row in rows
                            ],
                        )
                if not dry_run:
                    reset_sequences(cursor)
    finally:
        source.close()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("sqlite_path")
    parser.add_argument("--dsn", default=os.environ.get("POSTGRES_DSN"))
    parser.add_argument("--force-overwrite", action="store_true", help="allow replacing a non-empty PostgreSQL target")
    parser.add_argument("--dry-run", action="store_true", help="validate tables and report source row counts without writing")
    args = parser.parse_args()
    if not args.dsn:
        parser.error("--dsn or POSTGRES_DSN is required")
    migrate(args.sqlite_path, args.dsn, args.force_overwrite, args.dry_run)


if __name__ == "__main__":
    main()
