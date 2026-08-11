-- Remove the Stripe-based subscription feature.
-- Stripe is not usable in our launch market (Korea) and no payment
-- provider has been chosen yet, so the subscriptions table and all
-- billing logic are dropped. Indexes drop with the table.
DROP TABLE IF EXISTS subscriptions CASCADE;
