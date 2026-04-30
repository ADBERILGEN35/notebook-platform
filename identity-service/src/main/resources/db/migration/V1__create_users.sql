CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    email varchar(320) NOT NULL,
    name varchar(160) NOT NULL,
    avatar_url varchar(1024),
    password_hash varchar(512) NOT NULL,
    status varchar(40) NOT NULL,
    email_verified_at timestamptz,
    last_login_at timestamptz,
    password_changed_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_users_email_lower ON users (lower(email));
CREATE INDEX IF NOT EXISTS idx_users_status ON users (status);

