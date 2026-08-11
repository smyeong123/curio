-- Migrate user_preferences topics from legacy broad topics to new AI-focused topic hierarchy.
-- Users with old broad topics are migrated to relevant AI sub-topics.
-- This is a best-effort mapping; users can refine their selections in Settings.

-- Technology & AI → Large Language Models + Generative AI
UPDATE user_preferences
SET topics = array_replace(topics, 'Technology & AI', 'Large Language Models')
WHERE 'Technology & AI' = ANY(topics);

UPDATE user_preferences
SET topics = array_cat(topics, ARRAY['Generative AI'])
WHERE 'Large Language Models' = ANY(topics)
  AND NOT ('Generative AI' = ANY(topics));

-- Business & Finance → AI Funding & Deals + Enterprise AI Adoption
UPDATE user_preferences
SET topics = array_replace(topics, 'Business & Finance', 'AI Funding & Deals')
WHERE 'Business & Finance' = ANY(topics);

UPDATE user_preferences
SET topics = array_cat(topics, ARRAY['Enterprise AI Adoption'])
WHERE 'AI Funding & Deals' = ANY(topics)
  AND NOT ('Enterprise AI Adoption' = ANY(topics));

-- Science & Health → AI in Healthcare + Academic AI Research
UPDATE user_preferences
SET topics = array_replace(topics, 'Science & Health', 'AI in Healthcare')
WHERE 'Science & Health' = ANY(topics);

UPDATE user_preferences
SET topics = array_cat(topics, ARRAY['Academic AI Research'])
WHERE 'AI in Healthcare' = ANY(topics)
  AND NOT ('Academic AI Research' = ANY(topics));

-- World News → EU AI Act & Global Policy
UPDATE user_preferences
SET topics = array_replace(topics, 'World News', 'EU AI Act & Global Policy')
WHERE 'World News' = ANY(topics);

-- US Politics → US AI Policy
UPDATE user_preferences
SET topics = array_replace(topics, 'US Politics', 'US AI Policy')
WHERE 'US Politics' = ANY(topics);

-- Climate & Environment → AI Energy & Sustainability
UPDATE user_preferences
SET topics = array_replace(topics, 'Climate & Environment', 'AI Energy & Sustainability')
WHERE 'Climate & Environment' = ANY(topics);

-- Startups & Entrepreneurship → AI Product Launches
UPDATE user_preferences
SET topics = array_replace(topics, 'Startups & Entrepreneurship', 'AI Product Launches')
WHERE 'Startups & Entrepreneurship' = ANY(topics);

-- Sports → AI in Education (best-effort for non-AI topics)
UPDATE user_preferences
SET topics = array_replace(topics, 'Sports', 'AI in Education')
WHERE 'Sports' = ANY(topics);

-- Entertainment → AI for Creative Work
UPDATE user_preferences
SET topics = array_replace(topics, 'Entertainment', 'AI for Creative Work')
WHERE 'Entertainment' = ANY(topics);

-- Lifestyle & Wellness → AI & Future of Work
UPDATE user_preferences
SET topics = array_replace(topics, 'Lifestyle & Wellness', 'AI & Future of Work')
WHERE 'Lifestyle & Wellness' = ANY(topics);

-- Remove any duplicates that may have been introduced
UPDATE user_preferences
SET topics = ARRAY(SELECT DISTINCT unnest(topics) ORDER BY 1)
WHERE array_length(topics, 1) > 0;
