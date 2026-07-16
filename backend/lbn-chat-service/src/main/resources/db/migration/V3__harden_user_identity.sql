-- Let MySQL allocate user identifiers atomically. Explicit ids in the seed remain valid,
-- and the next generated id starts above the existing maximum.
ALTER TABLE lbn_user MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;
