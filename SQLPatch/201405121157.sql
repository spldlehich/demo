
-- Table: repo.role_data

-- DROP TABLE repo.role_data;

CREATE TABLE repo.role_data
(
  dataid text NOT NULL,
  name text NOT NULL,
  CONSTRAINT role_data_pkey PRIMARY KEY (dataid )
);

-- Table: repo.role

-- DROP TABLE repo.role;

CREATE TABLE repo.role
(
  id text NOT NULL,
  staticid text NOT NULL,
  dataid text NOT NULL,
  CONSTRAINT role_pkey PRIMARY KEY (id ),
  CONSTRAINT role_dataid_fkey FOREIGN KEY (dataid)
      REFERENCES repo.role_data (dataid) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED
);

-- Table: repo.role_group

-- DROP TABLE repo.role_group;

CREATE TABLE repo.role_group
(
  from_id text NOT NULL,
  to_id text NOT NULL,
  CONSTRAINT rpu_pk_role_group PRIMARY KEY (from_id , to_id ),
  CONSTRAINT role_group_from_id_fkey FOREIGN KEY (from_id)
      REFERENCES repo.role (id) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED,
  CONSTRAINT role_group_to_id_fkey FOREIGN KEY (to_id)
      REFERENCES repo."group" (id) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED
);

-- Index: repo.repo_idx_role_group_1

-- DROP INDEX repo.repo_idx_role_group_1;

CREATE INDEX repo_idx_role_group_1
  ON repo.role_group
  USING btree
  (from_id, to_id);

-- Index: repo.repo_idx_role_group_2

-- DROP INDEX repo.repo_idx_role_group_2;

CREATE INDEX repo_idx_role_group_2
  ON repo.role_group
  USING btree
  (to_id, from_id);

-- Index: repo.repo_idx_role_group_3

-- DROP INDEX repo.repo_idx_role_group_3;

CREATE INDEX repo_idx_role_group_3
  ON repo.role_group
  USING btree
  (from_id);

-- Index: repo.repo_idx_role_group_4

-- DROP INDEX repo.repo_idx_role_group_4;

CREATE INDEX repo_idx_role_group_4
  ON repo.role_group
  USING btree
  (to_id);



-- Table: repo.permissionrole_data

-- DROP TABLE repo.permissionrole_data;

CREATE TABLE repo.permissionrole_data
(
  dataid text NOT NULL,
  kind text NOT NULL,
  mask bigint NOT NULL,
  CONSTRAINT permissionrole_data_pkey PRIMARY KEY (dataid )
);


-- Table: repo.permissionrole

-- DROP TABLE repo.permissionrole;

CREATE TABLE repo.permissionrole
(
  id text NOT NULL,
  staticid text NOT NULL,
  dataid text NOT NULL,
  CONSTRAINT permissionrole_pkey PRIMARY KEY (id ),
  CONSTRAINT permissionrole_dataid_fkey FOREIGN KEY (dataid)
      REFERENCES repo.permissionrole_data (dataid) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED
);


-- Table: repo.permissionrole_role

-- DROP TABLE repo.permissionrole_role;

CREATE TABLE repo.permissionrole_role
(
  from_id text NOT NULL,
  to_id text NOT NULL,
  CONSTRAINT rpu_pk_permissionrole PRIMARY KEY (from_id , to_id ),
  CONSTRAINT permissionrole_role_from_id_fkey FOREIGN KEY (from_id)
      REFERENCES repo.permissionrole (id) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED,
  CONSTRAINT permissionrole_role_to_id_fkey FOREIGN KEY (to_id)
      REFERENCES repo.role (id) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED
);

-- Index: repo.repo_idx_permissionrole_role_1

-- DROP INDEX repo.repo_idx_permissionrole_role_1;

CREATE INDEX repo_idx_permissionrole_role_1
  ON repo.permissionrole_role
  USING btree
  (from_id, to_id);

-- Index: repo.repo_idx_permissionrole_role_2

-- DROP INDEX repo.repo_idx_permissionrole_role_2;

CREATE INDEX repo_idx_permissionrole_role_2
  ON repo.permissionrole_role
  USING btree
  (to_id, from_id );

-- Index: repo.repo_idx_permissionrole_role_3

-- DROP INDEX repo.repo_idx_permissionrole_role_3;

CREATE INDEX repo_idx_permissionrole_role_3
  ON repo.permissionrole_role
  USING btree
  (from_id);

-- Index: repo.repo_idx_permissionrole_role_4

-- DROP INDEX repo.repo_idx_permissionrole_role_4;

CREATE INDEX repo_idx_permissionrole_role_4
  ON repo.permissionrole_role
  USING btree
  (to_id);
