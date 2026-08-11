-- V16: Curio pivot — rewrite the taxonomy from 27 broad AI topics to a tighter
-- set focused on AI *model* news (releases, features, emerging agentic systems).
-- This migrates existing user_preferences.topics arrays to the new vocabulary.
-- Anything unmappable is dropped; users keeping fewer than 3 topics will be
-- re-prompted by the Settings UI on next visit.
--
-- Mapping rationale (best-effort):
--   LLM-adjacent research → Claude (Anthropic) as the best general-purpose proxy
--   Frontier-lab specific → GPT / Gemini / Claude as appropriate
--   Open-source models   → Llama, DeepSeek, Qwen, Mistral
--   Dev-tool / MLOps     → Agent Frameworks & SDKs, Claude Code
--   Benchmarks           → Benchmarks & Evaluations
--   Hardware / pricing   → Pricing & Availability
--   Multimodal / vision  → Multimodal (Vision, Audio, Video)
--   Safety / reasoning   → Reasoning & Context
--   Society / policy     → dropped (off-focus; users re-select)

-- Helper: trim each topic in place. Runs as UPDATE iterations so we can remove
-- legacy values cleanly.

-- 1) Straight renames.
UPDATE user_preferences SET topics = array_replace(topics, 'OpenAI & Frontier Labs', 'GPT & ChatGPT (OpenAI)') WHERE 'OpenAI & Frontier Labs' = ANY(topics);
UPDATE user_preferences SET topics = array_replace(topics, 'Large Language Models', 'Claude (Anthropic)') WHERE 'Large Language Models' = ANY(topics);
UPDATE user_preferences SET topics = array_replace(topics, 'Generative AI', 'New & Emerging Models') WHERE 'Generative AI' = ANY(topics);
UPDATE user_preferences SET topics = array_replace(topics, 'Deep Learning', 'Reasoning & Context') WHERE 'Deep Learning' = ANY(topics);
UPDATE user_preferences SET topics = array_replace(topics, 'Reinforcement Learning', 'Reasoning & Context') WHERE 'Reinforcement Learning' = ANY(topics);
UPDATE user_preferences SET topics = array_replace(topics, 'AI Safety & Alignment', 'Reasoning & Context') WHERE 'AI Safety & Alignment' = ANY(topics);
UPDATE user_preferences SET topics = array_replace(topics, 'Computer Vision & Multimodal', 'Multimodal (Vision, Audio, Video)') WHERE 'Computer Vision & Multimodal' = ANY(topics);
UPDATE user_preferences SET topics = array_replace(topics, 'AI Benchmarks & Evaluation', 'Benchmarks & Evaluations') WHERE 'AI Benchmarks & Evaluation' = ANY(topics);
UPDATE user_preferences SET topics = array_replace(topics, 'Open Source AI Models', 'Llama (Meta AI)') WHERE 'Open Source AI Models' = ANY(topics);
UPDATE user_preferences SET topics = array_replace(topics, 'AI APIs & Developer Tools', 'Agent Frameworks & SDKs') WHERE 'AI APIs & Developer Tools' = ANY(topics);
UPDATE user_preferences SET topics = array_replace(topics, 'MLOps & AI Infrastructure', 'Agent Frameworks & SDKs') WHERE 'MLOps & AI Infrastructure' = ANY(topics);
UPDATE user_preferences SET topics = array_replace(topics, 'AI Product Launches', 'New & Emerging Models') WHERE 'AI Product Launches' = ANY(topics);
UPDATE user_preferences SET topics = array_replace(topics, 'AI Hardware & Chips', 'Pricing & Availability') WHERE 'AI Hardware & Chips' = ANY(topics);
UPDATE user_preferences SET topics = array_replace(topics, 'AI Funding & Deals', 'Pricing & Availability') WHERE 'AI Funding & Deals' = ANY(topics);
UPDATE user_preferences SET topics = array_replace(topics, 'Enterprise AI Adoption', 'Pricing & Availability') WHERE 'Enterprise AI Adoption' = ANY(topics);
UPDATE user_preferences SET topics = array_replace(topics, 'Academic AI Research', 'Benchmarks & Evaluations') WHERE 'Academic AI Research' = ANY(topics);
UPDATE user_preferences SET topics = array_replace(topics, 'AI for Creative Work', 'Multimodal (Vision, Audio, Video)') WHERE 'AI for Creative Work' = ANY(topics);
UPDATE user_preferences SET topics = array_replace(topics, 'AGI & Superintelligence', 'New & Emerging Models') WHERE 'AGI & Superintelligence' = ANY(topics);
UPDATE user_preferences SET topics = array_replace(topics, 'AI Energy & Sustainability', 'Pricing & Availability') WHERE 'AI Energy & Sustainability' = ANY(topics);

-- 2) Off-focus legacy topics: drop them. array_remove returns a new array with
--    every occurrence of the element removed.
UPDATE user_preferences SET topics = array_remove(topics, 'AI in Healthcare');
UPDATE user_preferences SET topics = array_remove(topics, 'AI in Finance & Legal');
UPDATE user_preferences SET topics = array_remove(topics, 'AI in Education');
UPDATE user_preferences SET topics = array_remove(topics, 'US AI Policy');
UPDATE user_preferences SET topics = array_remove(topics, 'EU AI Act & Global Policy');
UPDATE user_preferences SET topics = array_remove(topics, 'Corporate AI Ethics');
UPDATE user_preferences SET topics = array_remove(topics, 'AI & Future of Work');
UPDATE user_preferences SET topics = array_remove(topics, 'AI Bias & Fairness');

-- 3) Deduplicate after the mapping (several legacy topics collapse onto the
--    same new topic, e.g. Deep Learning + Reinforcement Learning both →
--    Reasoning & Context).
UPDATE user_preferences
SET topics = ARRAY(SELECT DISTINCT unnest(topics) ORDER BY 1)
WHERE topics IS NOT NULL AND array_length(topics, 1) > 0;
