-- Adds 'infant' to the passenger type CHECK constraint.
--
-- Infant is a FLIGHT fare type (a lap infant on an adult's ticket), not a hotel concept: hotels model
-- children by their exact age and price them from age bands, so an under-2 in a hotel-only booking is
-- simply a child. The reservation module enforces that split (an INFANT traveller requires a flight in
-- the booking); this migration only widens the storage constraint so the type can be persisted.
--
-- Only the constraint changes: existing rows hold 'adult'/'child' and remain valid.
ALTER TABLE passengers DROP CONSTRAINT ck_passengers_type;

ALTER TABLE passengers
    ADD CONSTRAINT ck_passengers_type CHECK (passenger_type IN ('adult', 'child', 'infant'));
