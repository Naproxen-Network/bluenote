#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate MySQL schema.sql and seed.sql from the generated JSON."""
import json, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GEN = os.path.join(ROOT, "data", "generated")
SQL_DIR = os.path.join(ROOT, "backend", "sql")
os.makedirs(SQL_DIR, exist_ok=True)

users = json.load(open(os.path.join(GEN, "users.json"), encoding="utf-8"))
posts = json.load(open(os.path.join(GEN, "posts.json"), encoding="utf-8"))


def esc(s):
    if s is None:
        return ""
    return str(s).replace("\\", "\\\\").replace("'", "''")


schema = """-- 小蓝书 (Little Blue Note) schema
CREATE DATABASE IF NOT EXISTS little_blue_note DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE little_blue_note;

DROP TABLE IF EXISTS lbn_favorite;
DROP TABLE IF EXISTS lbn_comment;
DROP TABLE IF EXISTS lbn_like;
DROP TABLE IF EXISTS lbn_follow;
DROP TABLE IF EXISTS lbn_post;
DROP TABLE IF EXISTS lbn_user;
DROP TABLE IF EXISTS lbn_admin;

CREATE TABLE lbn_user (
  id            BIGINT PRIMARY KEY,
  username      VARCHAR(64)  NOT NULL UNIQUE,
  password      VARCHAR(128) NOT NULL,
  display_name  VARCHAR(128) NOT NULL,
  party         VARCHAR(128),
  leaning       VARCHAR(64),
  gender        VARCHAR(16),
  position      VARCHAR(255),
  alma_mater    VARCHAR(255),
  education_level VARCHAR(64),
  state         VARCHAR(64),
  bio           TEXT,
  interests     VARCHAR(255),
  committee_id  BIGINT,
  avatar        VARCHAR(512),
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE lbn_admin (
  id        BIGINT PRIMARY KEY AUTO_INCREMENT,
  username  VARCHAR(64) NOT NULL UNIQUE,
  password  VARCHAR(128) NOT NULL,
  name      VARCHAR(128)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE lbn_post (
  id          BIGINT PRIMARY KEY,
  author_id   BIGINT NOT NULL,
  field       VARCHAR(64),
  content     TEXT NOT NULL,
  tags        VARCHAR(255),
  image       VARCHAR(255),
  like_count  INT DEFAULT 0,
  comment_count INT DEFAULT 0,
  favorite_count INT DEFAULT 0,
  view_count  INT DEFAULT 0,
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_author (author_id),
  INDEX idx_field (field)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE lbn_like (
  id        BIGINT PRIMARY KEY AUTO_INCREMENT,
  post_id   BIGINT NOT NULL,
  user_id   BIGINT NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_like (post_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE lbn_favorite (
  id        BIGINT PRIMARY KEY AUTO_INCREMENT,
  post_id   BIGINT NOT NULL,
  user_id   BIGINT NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_fav (post_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE lbn_comment (
  id        BIGINT PRIMARY KEY AUTO_INCREMENT,
  post_id   BIGINT NOT NULL,
  user_id   BIGINT NOT NULL,
  content   VARCHAR(1000) NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_post (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE lbn_follow (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  follower_id BIGINT NOT NULL,
  followee_id BIGINT NOT NULL,
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_follow (follower_id, followee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
"""

with open(os.path.join(SQL_DIR, "schema.sql"), "w", encoding="utf-8") as f:
    f.write(schema)

lines = ["USE little_blue_note;", "SET NAMES utf8mb4;"]
# default password for every seeded user: lbn123456  (plain for demo; service hashes on real signup)
lines.append("INSERT INTO lbn_admin (username, password, name) VALUES ('admin', 'admin123', 'Platform Administrator');")

vals = []
for u in users:
    interests = "|".join(u.get("interests", []))
    vals.append(
        "({id},'{un}','lbn123456','{dn}','{party}','{lean}','{gender}','{pos}','{alma}','{edu}','{state}','{bio}','{interests}',{cid},'{avatar}')".format(
            id=u["id"], un=esc(u["username"]), dn=esc(u["displayName"]), party=esc(u.get("party")),
            lean=esc(u.get("leaning")), gender=esc(u.get("gender")), pos=esc(u.get("position")),
            alma=esc(u.get("almaMater")), edu=esc(u.get("educationLevel")), state=esc(u.get("state")),
            bio=esc(u.get("bio")), interests=esc(interests),
            cid=(u.get("committeeId") if u.get("committeeId") is not None else "NULL"),
            avatar=esc(u.get("avatar") or ""),
        )
    )
# chunk inserts
CHUNK = 100
cols = "(id,username,password,display_name,party,leaning,gender,position,alma_mater,education_level,state,bio,interests,committee_id,avatar)"
for i in range(0, len(vals), CHUNK):
    lines.append(f"INSERT INTO lbn_user {cols} VALUES\n" + ",\n".join(vals[i:i+CHUNK]) + ";")

pvals = []
for p in posts:
    pvals.append(
        "({id},{aid},'{field}','{content}','{tags}','{image}')".format(
            id=p["id"], aid=p["authorId"], field=esc(p["field"]),
            content=esc(p["content"]), tags=esc(p["tags"]), image=esc(p["image"]))
    )
pcols = "(id,author_id,field,content,tags,image)"
for i in range(0, len(pvals), CHUNK):
    lines.append(f"INSERT INTO lbn_post {pcols} VALUES\n" + ",\n".join(pvals[i:i+CHUNK]) + ";")

with open(os.path.join(SQL_DIR, "seed.sql"), "w", encoding="utf-8") as f:
    f.write("\n".join(lines) + "\n")

print(f"wrote schema.sql and seed.sql (users={len(users)}, posts={len(posts)})")
