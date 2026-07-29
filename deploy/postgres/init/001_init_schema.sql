BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

CREATE TABLE IF NOT EXISTS app_user (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_subject VARCHAR(128) NOT NULL UNIQUE,
    email VARCHAR(320) UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT app_user_status_chk
        CHECK (status IN ('ACTIVE', 'DISABLED', 'DELETED')),
    CONSTRAINT app_user_metadata_chk
        CHECK (jsonb_typeof(metadata) = 'object')
);

CREATE TABLE IF NOT EXISTS chat_conversation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    title VARCHAR(200) NOT NULL DEFAULT 'New conversation',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    project_name VARCHAR(200),
    current_jdk_version VARCHAR(50),
    current_spring_boot_version VARCHAR(50),
    target_jdk_version VARCHAR(50),
    target_spring_boot_version VARCHAR(50),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    last_message_at TIMESTAMPTZ,
    lock_version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT chat_conversation_status_chk
        CHECK (status IN ('ACTIVE', 'ARCHIVED', 'DELETED')),
    CONSTRAINT chat_conversation_lock_version_chk
        CHECK (lock_version >= 0),
    CONSTRAINT chat_conversation_metadata_chk
        CHECK (jsonb_typeof(metadata) = 'object')
);

CREATE TABLE IF NOT EXISTS chat_message (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL
        REFERENCES chat_conversation(id) ON DELETE CASCADE,
    parent_message_id UUID REFERENCES chat_message(id) ON DELETE SET NULL,
    sequence_no BIGINT NOT NULL,
    sender_role VARCHAR(20) NOT NULL,
    message_type VARCHAR(30) NOT NULL DEFAULT 'TEXT',
    content_format VARCHAR(20) NOT NULL DEFAULT 'MARKDOWN',
    content TEXT NOT NULL DEFAULT '',
    model_provider VARCHAR(50),
    model_name VARCHAR(100),
    input_tokens INTEGER,
    output_tokens INTEGER,
    latency_ms INTEGER,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    error_message TEXT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chat_message_conversation_sequence_uq
        UNIQUE (conversation_id, sequence_no),
    CONSTRAINT chat_message_sequence_chk
        CHECK (sequence_no > 0),
    CONSTRAINT chat_message_sender_role_chk
        CHECK (sender_role IN ('SYSTEM', 'USER', 'ASSISTANT', 'TOOL')),
    CONSTRAINT chat_message_type_chk
        CHECK (message_type IN (
            'TEXT', 'DIAGNOSIS_REQUEST', 'DIAGNOSIS_RESPONSE',
            'TOOL_CALL', 'TOOL_RESPONSE', 'ERROR'
        )),
    CONSTRAINT chat_message_content_format_chk
        CHECK (content_format IN ('PLAIN_TEXT', 'MARKDOWN', 'JSON')),
    CONSTRAINT chat_message_status_chk
        CHECK (status IN ('PENDING', 'STREAMING', 'COMPLETED', 'FAILED')),
    CONSTRAINT chat_message_input_tokens_chk
        CHECK (input_tokens IS NULL OR input_tokens >= 0),
    CONSTRAINT chat_message_output_tokens_chk
        CHECK (output_tokens IS NULL OR output_tokens >= 0),
    CONSTRAINT chat_message_latency_chk
        CHECK (latency_ms IS NULL OR latency_ms >= 0),
    CONSTRAINT chat_message_metadata_chk
        CHECK (jsonb_typeof(metadata) = 'object')
);

CREATE TABLE IF NOT EXISTS chat_attachment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id UUID NOT NULL REFERENCES chat_message(id) ON DELETE CASCADE,
    artifact_type VARCHAR(30) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    media_type VARCHAR(100),
    file_size_bytes BIGINT NOT NULL,
    sha256 CHAR(64),
    content_text TEXT,
    storage_uri TEXT,
    extraction_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    parsed_content JSONB,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chat_attachment_artifact_type_chk
        CHECK (artifact_type IN ('POM_XML', 'ERROR_LOG', 'DEPENDENCY_LIST', 'OTHER')),
    CONSTRAINT chat_attachment_size_chk
        CHECK (file_size_bytes >= 0),
    CONSTRAINT chat_attachment_sha256_chk
        CHECK (sha256 IS NULL OR sha256 ~ '^[0-9a-fA-F]{64}$'),
    CONSTRAINT chat_attachment_storage_chk
        CHECK (content_text IS NOT NULL OR storage_uri IS NOT NULL),
    CONSTRAINT chat_attachment_extraction_status_chk
        CHECK (extraction_status IN ('PENDING', 'COMPLETED', 'FAILED')),
    CONSTRAINT chat_attachment_parsed_content_chk
        CHECK (parsed_content IS NULL OR jsonb_typeof(parsed_content) = 'object'),
    CONSTRAINT chat_attachment_metadata_chk
        CHECK (jsonb_typeof(metadata) = 'object')
);

CREATE TABLE IF NOT EXISTS diagnosis_run (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL
        REFERENCES chat_conversation(id) ON DELETE CASCADE,
    request_message_id UUID NOT NULL REFERENCES chat_message(id) ON DELETE RESTRICT,
    response_message_id UUID REFERENCES chat_message(id) ON DELETE SET NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    question TEXT,
    project_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    target_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    model_provider VARCHAR(50),
    model_name VARCHAR(100),
    prompt_version VARCHAR(50),
    summary TEXT,
    raw_result JSONB,
    error_code VARCHAR(100),
    error_detail TEXT,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT diagnosis_run_status_chk
        CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    CONSTRAINT diagnosis_run_project_snapshot_chk
        CHECK (jsonb_typeof(project_snapshot) = 'object'),
    CONSTRAINT diagnosis_run_target_snapshot_chk
        CHECK (jsonb_typeof(target_snapshot) = 'object'),
    CONSTRAINT diagnosis_run_raw_result_chk
        CHECK (raw_result IS NULL OR jsonb_typeof(raw_result) = 'object'),
    CONSTRAINT diagnosis_run_time_chk
        CHECK (completed_at IS NULL OR started_at IS NULL OR completed_at >= started_at)
);

CREATE TABLE IF NOT EXISTS diagnosis_risk (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    diagnosis_id UUID NOT NULL REFERENCES diagnosis_run(id) ON DELETE CASCADE,
    category VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    likelihood SMALLINT,
    impact SMALLINT,
    affected_component VARCHAR(200),
    title VARCHAR(300) NOT NULL,
    description TEXT NOT NULL,
    mitigation TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT diagnosis_risk_severity_chk
        CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT diagnosis_risk_likelihood_chk
        CHECK (likelihood IS NULL OR likelihood BETWEEN 1 AND 5),
    CONSTRAINT diagnosis_risk_impact_chk
        CHECK (impact IS NULL OR impact BETWEEN 1 AND 5),
    CONSTRAINT diagnosis_risk_sort_order_chk
        CHECK (sort_order >= 0)
);

CREATE TABLE IF NOT EXISTS compatibility_issue (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    diagnosis_id UUID NOT NULL REFERENCES diagnosis_run(id) ON DELETE CASCADE,
    component VARCHAR(200) NOT NULL,
    issue_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    current_version VARCHAR(100),
    target_version VARCHAR(100),
    symptom TEXT,
    root_cause TEXT NOT NULL,
    confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT compatibility_issue_severity_chk
        CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT compatibility_issue_sort_order_chk
        CHECK (sort_order >= 0)
);

CREATE TABLE IF NOT EXISTS modification_suggestion (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    diagnosis_id UUID NOT NULL REFERENCES diagnosis_run(id) ON DELETE CASCADE,
    priority VARCHAR(10) NOT NULL,
    action_type VARCHAR(30) NOT NULL,
    file_path TEXT,
    title VARCHAR(300) NOT NULL,
    description TEXT NOT NULL,
    before_content TEXT,
    after_content TEXT,
    verification TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PROPOSED',
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT modification_suggestion_priority_chk
        CHECK (priority IN ('P0', 'P1', 'P2', 'P3')),
    CONSTRAINT modification_suggestion_action_type_chk
        CHECK (action_type IN (
            'DEPENDENCY', 'BUILD', 'CODE', 'CONFIGURATION',
            'DATABASE', 'TEST', 'DEPLOYMENT'
        )),
    CONSTRAINT modification_suggestion_status_chk
        CHECK (status IN ('PROPOSED', 'APPLIED', 'VERIFIED', 'SKIPPED')),
    CONSTRAINT modification_suggestion_sort_order_chk
        CHECK (sort_order >= 0)
);

CREATE TABLE IF NOT EXISTS knowledge_evidence (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    diagnosis_id UUID NOT NULL REFERENCES diagnosis_run(id) ON DELETE CASCADE,
    source_type VARCHAR(30) NOT NULL,
    source_url TEXT NOT NULL,
    title VARCHAR(500) NOT NULL,
    component VARCHAR(200),
    version_range VARCHAR(200),
    excerpt TEXT,
    relevance NUMERIC(5, 4),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    retrieved_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT knowledge_evidence_source_type_chk
        CHECK (source_type IN (
            'SPRING_DOC', 'JDK_DOC', 'MIGRATION_GUIDE',
            'RELEASE_NOTES', 'GITHUB_ISSUE', 'OTHER'
        )),
    CONSTRAINT knowledge_evidence_relevance_chk
        CHECK (relevance IS NULL OR relevance BETWEEN 0 AND 1),
    CONSTRAINT knowledge_evidence_metadata_chk
        CHECK (jsonb_typeof(metadata) = 'object')
);

CREATE TABLE IF NOT EXISTS upgrade_plan_step (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    diagnosis_id UUID NOT NULL REFERENCES diagnosis_run(id) ON DELETE CASCADE,
    sequence_no INTEGER NOT NULL,
    phase VARCHAR(30) NOT NULL,
    title VARCHAR(300) NOT NULL,
    description TEXT NOT NULL,
    prerequisites JSONB NOT NULL DEFAULT '[]'::jsonb,
    verification TEXT,
    rollback_action TEXT,
    estimated_effort VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT upgrade_plan_step_sequence_uq
        UNIQUE (diagnosis_id, sequence_no),
    CONSTRAINT upgrade_plan_step_sequence_chk
        CHECK (sequence_no > 0),
    CONSTRAINT upgrade_plan_step_phase_chk
        CHECK (phase IN (
            'PREPARATION', 'BUILD', 'SOURCE_CODE', 'DATA',
            'TESTING', 'DEPLOYMENT', 'ROLLBACK'
        )),
    CONSTRAINT upgrade_plan_step_prerequisites_chk
        CHECK (jsonb_typeof(prerequisites) = 'array'),
    CONSTRAINT upgrade_plan_step_status_chk
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'SKIPPED'))
);

CREATE INDEX IF NOT EXISTS app_user_active_idx
    ON app_user (created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS chat_conversation_user_updated_idx
    ON chat_conversation (user_id, updated_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS chat_conversation_last_message_idx
    ON chat_conversation (last_message_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS chat_message_conversation_created_idx
    ON chat_message (conversation_id, created_at, id);

CREATE INDEX IF NOT EXISTS chat_message_parent_idx
    ON chat_message (parent_message_id)
    WHERE parent_message_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS chat_message_content_search_idx
    ON chat_message USING GIN (to_tsvector('simple', content));

CREATE INDEX IF NOT EXISTS chat_message_metadata_gin_idx
    ON chat_message USING GIN (metadata);

CREATE INDEX IF NOT EXISTS chat_attachment_message_idx
    ON chat_attachment (message_id, created_at);

CREATE INDEX IF NOT EXISTS chat_attachment_sha256_idx
    ON chat_attachment (sha256)
    WHERE sha256 IS NOT NULL;

CREATE INDEX IF NOT EXISTS diagnosis_run_conversation_created_idx
    ON diagnosis_run (conversation_id, created_at DESC);

CREATE INDEX IF NOT EXISTS diagnosis_run_status_idx
    ON diagnosis_run (status, created_at)
    WHERE status IN ('QUEUED', 'RUNNING');

CREATE INDEX IF NOT EXISTS diagnosis_risk_diagnosis_idx
    ON diagnosis_risk (diagnosis_id, severity, sort_order);

CREATE INDEX IF NOT EXISTS compatibility_issue_diagnosis_idx
    ON compatibility_issue (diagnosis_id, severity, sort_order);

CREATE INDEX IF NOT EXISTS modification_suggestion_diagnosis_idx
    ON modification_suggestion (diagnosis_id, priority, sort_order);

CREATE INDEX IF NOT EXISTS knowledge_evidence_diagnosis_idx
    ON knowledge_evidence (diagnosis_id, relevance DESC NULLS LAST);

CREATE INDEX IF NOT EXISTS knowledge_evidence_source_idx
    ON knowledge_evidence (source_type, component);

CREATE INDEX IF NOT EXISTS upgrade_plan_step_diagnosis_idx
    ON upgrade_plan_step (diagnosis_id, sequence_no);

DROP TRIGGER IF EXISTS app_user_set_updated_at ON app_user;
CREATE TRIGGER app_user_set_updated_at
BEFORE UPDATE ON app_user
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS chat_conversation_set_updated_at ON chat_conversation;
CREATE TRIGGER chat_conversation_set_updated_at
BEFORE UPDATE ON chat_conversation
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS diagnosis_run_set_updated_at ON diagnosis_run;
CREATE TRIGGER diagnosis_run_set_updated_at
BEFORE UPDATE ON diagnosis_run
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS modification_suggestion_set_updated_at ON modification_suggestion;
CREATE TRIGGER modification_suggestion_set_updated_at
BEFORE UPDATE ON modification_suggestion
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS upgrade_plan_step_set_updated_at ON upgrade_plan_step;
CREATE TRIGGER upgrade_plan_step_set_updated_at
BEFORE UPDATE ON upgrade_plan_step
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE OR REPLACE FUNCTION touch_conversation_after_message()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    UPDATE chat_conversation
    SET last_message_at = NEW.created_at
    WHERE id = NEW.conversation_id;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS chat_message_touch_conversation ON chat_message;
CREATE TRIGGER chat_message_touch_conversation
AFTER INSERT ON chat_message
FOR EACH ROW EXECUTE FUNCTION touch_conversation_after_message();

COMMENT ON TABLE chat_conversation IS
    'Durable user-visible conversation history. Do not use as an evicting LLM memory window.';
COMMENT ON TABLE chat_message IS
    'Immutable ordered messages, including user, assistant, system, and tool messages.';
COMMENT ON TABLE diagnosis_run IS
    'One upgrade diagnosis execution with immutable input snapshots and structured output.';

COMMIT;
