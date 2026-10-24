-- Users ---------------------------------------------------------------------
CREATE TABLE users (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name  VARCHAR(100) NOT NULL,
    email      VARCHAR(255) NOT NULL,
    password   VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email)
);

-- Todos ---------------------------------------------------------------------
CREATE TABLE todos (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    title       VARCHAR(200) NOT NULL,
    description TEXT,
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    priority    VARCHAR(20)  NOT NULL DEFAULT 'MEDIUM',
    due_date    DATE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    user_id     UUID         NOT NULL,
    CONSTRAINT fk_todos_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_todos_status CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT chk_todos_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT chk_todos_title_not_blank CHECK (length(btrim(title)) > 0)
);

CREATE INDEX idx_todos_user_id         ON todos (user_id);
CREATE INDEX idx_todos_user_status     ON todos (user_id, status);
CREATE INDEX idx_todos_user_created_at ON todos (user_id, created_at DESC);
CREATE INDEX idx_todos_user_due_date   ON todos (user_id, due_date);
