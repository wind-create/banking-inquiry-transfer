CREATE TABLE users (
    username VARCHAR(36) PRIMARY KEY,
    password VARCHAR(500) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,

    created_on TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_on TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(150) NOT NULL DEFAULT 'SYSTEM',
    updated_by VARCHAR(150) NOT NULL DEFAULT 'SYSTEM'
);

CREATE TABLE authorities (
    username VARCHAR(36) NOT NULL,
    authority VARCHAR(100) NOT NULL,

    created_on TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_on TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(150) NOT NULL DEFAULT 'SYSTEM',
    updated_by VARCHAR(150) NOT NULL DEFAULT 'SYSTEM',

    CONSTRAINT pk_authorities
        PRIMARY KEY (username, authority),

    CONSTRAINT fk_authorities_users
        FOREIGN KEY (username)
        REFERENCES users(username)
        ON DELETE CASCADE
);