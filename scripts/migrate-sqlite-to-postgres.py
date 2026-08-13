#!/usr/bin/env python3
"""One-time, transactional SQLite to PostgreSQL data migration for Auto HR."""

import argparse
import os
import sqlite3
import sys

try:
    import psycopg
except ImportError as error:
    raise SystemExit("Install psycopg[binary] before running this migration.") from error


TABLES = [
    "hr_department", "hr_employee", "hr_integration_binding",
    "recruitment_job", "recruitment_candidate", "recruitment_resume_file",
    "interview_batch", "interview_question", "interview_candidate", "interview_submission",
    "sys_user", "sys_audit_log", "interview_knowledge_base", "interview_knowledge_item",
    "interview_job_knowledge_weight", "interview_llm_config", "interview_process",
    "interview_ai_record", "interview_video_session",
]


def quote(identifier):
    return '"' + identifier.replace('"', '""') + '"'


def migrate(sqlite_path, postgres_dsn):
    source = sqlite3.connect(sqlite_path)
    source.row_factory = sqlite3.Row
    with psycopg.connect(postgres_dsn) as target:
        with target.cursor() as cursor:
            cursor.execute("SET CONSTRAINTS ALL DEFERRED")
            for table in TABLES:
                columns = [row[1] for row in source.execute(f"PRAGMA table_info({quote(table)})")]
                if not columns:
                    continue
                rows = source.execute(f"SELECT * FROM {quote(table)}").fetchall()
                cursor.execute(f"TRUNCATE TABLE {quote(table)} CASCADE")
                if rows:
                    column_list = ", ".join(quote(column) for column in columns)
                    placeholders = ", ".join(["%s"] * len(columns))
                    cursor.executemany(
                        f"INSERT INTO {quote(table)} ({column_list}) VALUES ({placeholders})",
                        [tuple(row[column] for column in columns) for row in rows],
                    )
                print(f"{table}: {len(rows)} rows")
            for table in TABLES:
                cursor.execute(
                    "SELECT column_name FROM information_schema.columns "
                    "WHERE table_schema = 'public' AND table_name = %s AND column_default LIKE 'nextval%%'",
                    (table,),
                )
                for (column,) in cursor.fetchall():
                    cursor.execute(
                        f"SELECT setval(pg_get_serial_sequence(%s, %s), COALESCE((SELECT MAX({quote(column)}) FROM {quote(table)}), 1), true)",
                        (table, column),
                    )
    source.close()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("sqlite_path")
    parser.add_argument("--dsn", default=os.environ.get("POSTGRES_DSN"))
    args = parser.parse_args()
    if not args.dsn:
        parser.error("--dsn or POSTGRES_DSN is required")
    migrate(args.sqlite_path, args.dsn)


if __name__ == "__main__":
    main()
