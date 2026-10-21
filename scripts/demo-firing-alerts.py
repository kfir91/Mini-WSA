#!/usr/bin/env python3
"""
Make demo alerts FIRING from existing Postgres rows.

Preferred: use the dashboard button "Make demo alerts FIRING"
(POST http://localhost:8080/demo/alerts/fire) — same logic in Java.

This CLI is a fallback when analytics is down. It only rewinds timestamps on
enough existing rows (does not invent events).

Requires: pip install psycopg2-binary
Env (optional): PGHOST PGPORT PGDATABASE PGUSER PGPASSWORD
"""
from __future__ import annotations

import os
import sys

try:
    import psycopg2
except ImportError:
    print("Install psycopg2-binary first: python -m pip install psycopg2-binary", file=sys.stderr)
    sys.exit(1)

DSN = dict(
    host=os.getenv("PGHOST", "127.0.0.1"),
    port=int(os.getenv("PGPORT", "5432")),
    dbname=os.getenv("PGDATABASE", "wsa"),
    user=os.getenv("PGUSER", "wsa"),
    password=os.getenv("PGPASSWORD", "change-me"),
)

# Volume inside last ~3 minutes — fires assignment/smoke, leaves BOT≥20, unique_ips≥5, XSS≥50 quiet.
NEED_5M = {
    "INJECTION": 12,
    "XSS": 8,
    "BOT": 12,
    "DOS": 12,
    "PROTOCOL_VIOLATION": 4,
    "RATE_LIMIT": 6,
    "DATA_LEAKAGE": 6,
}

TARGET_IPS = ["203.0.113.42", "198.51.100.23", "192.0.2.15"]


def main() -> None:
    conn = psycopg2.connect(**DSN)
    cur = conn.cursor()

    cur.execute(
        """
        UPDATE security_events
        SET event_timestamp = now() - interval '2 hours'
        WHERE event_timestamp > now() - interval '1 hour'
        """
    )
    print("cleared_recent", cur.rowcount)

    for category, n in NEED_5M.items():
        cur.execute(
            """
            UPDATE security_events e
            SET event_timestamp = now() - (random() * interval '3 minutes'),
                received_at = now()
            WHERE e.event_id IN (
              SELECT event_id FROM security_events
              WHERE category = %s
              ORDER BY event_id
              LIMIT %s
            )
            """,
            (category, n),
        )
        print(f"refreshed {category}: {cur.rowcount}")

    for i, ip in enumerate(TARGET_IPS):
        cur.execute(
            """
            UPDATE security_events e
            SET client_ip = %s,
                event_timestamp = now() - (%s * interval '20 seconds'),
                received_at = now()
            WHERE e.event_id = (
              SELECT event_id FROM security_events
              WHERE event_timestamp > now() - interval '5 minutes'
              ORDER BY event_id
              OFFSET %s LIMIT 1
            )
            """,
            (ip, i, i),
        )

    conn.commit()

    cur.execute(
        """
        SELECT category, count(*) FROM security_events
        WHERE event_timestamp > now() - interval '5 minutes'
        GROUP BY 1 ORDER BY 2 DESC
        """
    )
    print("last_5m_by_cat", cur.fetchall())
    cur.execute(
        """
        SELECT count(*) total, count(DISTINCT client_ip) ips
        FROM security_events
        WHERE event_timestamp > now() - interval '5 minutes'
        """
    )
    print("last_5m", cur.fetchone())
    print("Done. Refresh http://localhost:8080/v1/alerts/evaluate (5m windows age out quickly — re-run if needed).")
    conn.close()


if __name__ == "__main__":
    main()
