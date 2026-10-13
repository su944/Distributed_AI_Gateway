CREATE TABLE tenants (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    slug VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT tenants_name_not_blank CHECK (length(trim(name)) > 0),
    CONSTRAINT tenants_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);
CREATE TABLE api_keys (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    name VARCHAR(100) NOT NULL CHECK (length(trim(name)) > 0),
    public_id VARCHAR(32) NOT NULL UNIQUE CHECK (public_id ~ '^[a-f0-9]{32}$'),
    secret_hash VARCHAR(64) NOT NULL CHECK (secret_hash ~ '^[a-f0-9]{64}$'),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'REVOKED')),
    permissions VARCHAR(100) NOT NULL CHECK (permissions IN ('', 'CHAT_COMPLETIONS')),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT api_keys_revocation CHECK ((status = 'ACTIVE' AND revoked_at IS NULL) OR (status = 'REVOKED' AND revoked_at IS NOT NULL)),
    CONSTRAINT api_keys_expiry CHECK (expires_at IS NULL OR expires_at > created_at)
);
CREATE INDEX api_keys_tenant_created_idx ON api_keys(tenant_id, created_at DESC);
CREATE INDEX api_keys_tenant_status_idx ON api_keys(tenant_id, status);
