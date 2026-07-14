-- 小蓝书 (Little Blue Note) schema
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
