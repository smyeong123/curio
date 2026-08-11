-- V17: Bring-Your-Own-Key (BYOK).
-- Allow each user to plug in their own LLM API key (Claude / Gemini / OpenAI)
-- so digest generation is billed to them instead of the platform. Users with
-- a valid key get a subscription discount.
--
-- Security model:
--   * encrypted_key + key_iv hold AES-256-GCM ciphertext + nonce. The master
--     key lives in env (API_KEY_ENCRYPTION_KEY); rotating it requires
--     re-encrypting every row, so do that during a maintenance window.
--   * key_preview is `prefix...last4` for UI ("sk-ant-...XYZW"). It is the
--     ONLY part of the key any HTTP response ever includes.
--   * UNIQUE (user_id, provider) — one key per provider per user.

CREATE TABLE user_api_keys (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider      VARCHAR(32) NOT NULL,
    encrypted_key BYTEA NOT NULL,
    key_iv        BYTEA NOT NULL,
    key_preview   VARCHAR(64) NOT NULL,
    validated_at  TIMESTAMP WITH TIME ZONE,
    last_used_at  TIMESTAMP WITH TIME ZONE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT user_api_keys_provider_check CHECK (provider IN ('CLAUDE', 'GEMINI', 'OPENAI')),
    CONSTRAINT user_api_keys_user_provider_unique UNIQUE (user_id, provider)
);

CREATE INDEX idx_user_api_keys_user_id ON user_api_keys (user_id);
CREATE INDEX idx_user_api_keys_validated ON user_api_keys (user_id) WHERE validated_at IS NOT NULL;
