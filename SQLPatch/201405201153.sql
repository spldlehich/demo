
-- Column: srid

-- ALTER TABLE repo.user_data DROP COLUMN srid;

ALTER TABLE repo.user_data ADD COLUMN srid text;

-- Table: repo.grouplink_data

-- DROP TABLE repo.grouplink_data;

CREATE TABLE repo.grouplink_data
(
  dataid text NOT NULL,
  sgid text NOT NULL,
  CONSTRAINT grouplink_data_pkey PRIMARY KEY (dataid )
);
-- Table: repo.grouplink

-- DROP TABLE repo.grouplink;

CREATE TABLE repo.grouplink
(
  id text NOT NULL,
  staticid text NOT NULL,
  dataid text NOT NULL,
  CONSTRAINT grouplink_pkey PRIMARY KEY (id ),
  CONSTRAINT grouplink_dataid_fkey FOREIGN KEY (dataid)
      REFERENCES repo.grouplink_data (dataid) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED
);

-- Table: repo.grouplink_user

-- DROP TABLE repo.grouplink_user;

CREATE TABLE repo.grouplink_user
(
  from_id text NOT NULL,
  to_id text NOT NULL,
  CONSTRAINT rpu_pk_grouplink_user PRIMARY KEY (from_id , to_id ),
  CONSTRAINT grouplink_user_from_id_fkey FOREIGN KEY (from_id)
      REFERENCES repo.grouplink (id) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED,
  CONSTRAINT grouplink_user_to_id_fkey FOREIGN KEY (to_id)
      REFERENCES repo."user" (id) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED
);

-- Index: repo.repo_idx_grouplink_user_1

-- DROP INDEX repo.repo_idx_grouplink_user_1;

CREATE INDEX repo_idx_grouplink_user_1
  ON repo.grouplink_user
  USING btree
  (from_id, to_id);

-- Index: repo.repo_idx_grouplink_user_2

-- DROP INDEX repo.repo_idx_grouplink_user_2;

CREATE INDEX repo_idx_grouplink_user_2
  ON repo.grouplink_user
  USING btree
  (to_id, from_id);

-- Index: repo.repo_idx_grouplink_user_3

-- DROP INDEX repo.repo_idx_grouplink_user_3;

CREATE INDEX repo_idx_grouplink_user_3
  ON repo.grouplink_user
  USING btree
  (from_id);

-- Index: repo.repo_idx_grouplink_user_4

-- DROP INDEX repo.repo_idx_grouplink_user_4;

CREATE INDEX repo_idx_grouplink_user_4
  ON repo.grouplink_user
  USING btree
  (to_id);


