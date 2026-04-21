CREATE TABLE members (
     id BIGSERIAL PRIMARY KEY,
     email VARCHAR(255) NOT NULL,
     password VARCHAR(255) NOT NULL,
     name VARCHAR(100) NOT NULL,
     role VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',
     status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
     login_fail_count INT NOT NULL DEFAULT 0,
     last_login_at TIMESTAMP NULL,
     created_at TIMESTAMP NOT NULL DEFAULT NOW(),
     updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

     CONSTRAINT uq_members_email UNIQUE (email),
     CONSTRAINT chk_members_role CHECK (role IN ('CUSTOMER', 'ADMIN')),
     CONSTRAINT chk_members_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'LOCKED', 'DELETED'))
);
CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL,
    token VARCHAR(512) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_refresh_tokens_member_id UNIQUE (member_id),
    CONSTRAINT fk_refresh_tokens_member
        FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE
);