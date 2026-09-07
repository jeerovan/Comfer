-- Existing reporting schema, retained as a compatibility test fixture.
CREATE TABLE import_runs (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            package_name TEXT NOT NULL,
            version_code INTEGER NOT NULL,
            version_name TEXT NOT NULL,
            period_start TEXT NOT NULL,
            period_end TEXT NOT NULL,
            imported_at TEXT NOT NULL,
            issue_count INTEGER NOT NULL,
            crash_count INTEGER NOT NULL,
            anr_count INTEGER NOT NULL
        );
CREATE TABLE issue_snapshots (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            import_run_id INTEGER NOT NULL,
            package_name TEXT NOT NULL,
            version_code INTEGER NOT NULL,
            issue_id TEXT NOT NULL,
            affected_users INTEGER NOT NULL,
            events INTEGER NOT NULL,
            last_event_at TEXT,
            FOREIGN KEY (import_run_id) REFERENCES import_runs(id),
            FOREIGN KEY (package_name, version_code, issue_id)
                REFERENCES issues(package_name, version_code, issue_id),
            UNIQUE (import_run_id, package_name, version_code, issue_id)
        );
CREATE TABLE issues (
            package_name TEXT NOT NULL,
            version_code INTEGER NOT NULL,
            version_name TEXT NOT NULL,
            issue_id TEXT NOT NULL,
            type TEXT NOT NULL CHECK (type IN ('anr', 'crash')),
            affected_users INTEGER NOT NULL DEFAULT 0,
            events INTEGER NOT NULL DEFAULT 0,
            affected_users_percent REAL,
            title TEXT NOT NULL,
            cause TEXT,
            location TEXT,
            stack_trace TEXT,
            status TEXT NOT NULL DEFAULT 'pending'
                CHECK (status IN ('pending', 'resolved')),
            notes TEXT,
            play_console_uri TEXT,
            sample_report_id TEXT,
            last_event_at TEXT,
            first_seen_at TEXT NOT NULL,
            last_synced_at TEXT NOT NULL,
            resolved_at TEXT,
            present_in_latest_import INTEGER NOT NULL DEFAULT 1
                CHECK (present_in_latest_import IN (0, 1)),
            os_api_level INTEGER,
            device_brand TEXT,
            device_model TEXT,
            vcs_information TEXT,
            annotations_json TEXT NOT NULL DEFAULT '[]',
            period_start TEXT NOT NULL,
            period_end TEXT NOT NULL,
            raw_issue_json TEXT NOT NULL,
            raw_report_json TEXT,
            PRIMARY KEY (package_name, version_code, issue_id)
        );
CREATE INDEX idx_issues_last_event
            ON issues(version_code, last_event_at DESC);
CREATE INDEX idx_issues_status
            ON issues(version_code, status, type);
CREATE INDEX idx_snapshots_issue
            ON issue_snapshots(package_name, version_code, issue_id);
