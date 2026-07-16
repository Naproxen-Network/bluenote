-- 小蓝书 (Little Blue Note) schema
CREATE DATABASE IF NOT EXISTS little_blue_note DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE little_blue_note;

DROP TABLE IF EXISTS lbn_chat_report;
DROP TABLE IF EXISTS lbn_user_chat_restriction;
DROP TABLE IF EXISTS lbn_chat_outbox;
DROP TABLE IF EXISTS lbn_message;
DROP TABLE IF EXISTS lbn_conversation_member;
DROP TABLE IF EXISTS lbn_conversation;
DROP TABLE IF EXISTS lbn_user_block;
DROP TABLE IF EXISTS lbn_friend_event;
DROP TABLE IF EXISTS lbn_friend_relation;
DROP TABLE IF EXISTS lbn_favorite;
DROP TABLE IF EXISTS lbn_comment;
DROP TABLE IF EXISTS lbn_like;
DROP TABLE IF EXISTS lbn_follow;
DROP TABLE IF EXISTS lbn_post;
DROP TABLE IF EXISTS lbn_user;
DROP TABLE IF EXISTS lbn_admin;

CREATE TABLE lbn_user (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
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

CREATE TABLE lbn_friend_relation (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_low_id     BIGINT NOT NULL,
  user_high_id    BIGINT NOT NULL,
  requester_id    BIGINT NOT NULL,
  request_message VARCHAR(200),
  status          VARCHAR(24) NOT NULL,
  requested_at    DATETIME(3) NOT NULL,
  responded_at    DATETIME(3),
  version         INT NOT NULL DEFAULT 0,
  created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_friend_pair (user_low_id, user_high_id),
  KEY idx_friend_low_status (user_low_id, status),
  KEY idx_friend_high_status (user_high_id, status),
  KEY idx_friend_requester_status (requester_id, status),
  CONSTRAINT fk_friend_low FOREIGN KEY (user_low_id) REFERENCES lbn_user(id),
  CONSTRAINT fk_friend_high FOREIGN KEY (user_high_id) REFERENCES lbn_user(id),
  CONSTRAINT fk_friend_requester FOREIGN KEY (requester_id) REFERENCES lbn_user(id),
  CONSTRAINT ck_friend_order CHECK (user_low_id < user_high_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE lbn_friend_event (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  relation_id BIGINT NOT NULL,
  actor_id    BIGINT NOT NULL,
  peer_id     BIGINT NOT NULL,
  action      VARCHAR(32) NOT NULL,
  detail      VARCHAR(500),
  created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_friend_event_relation (relation_id, id),
  KEY idx_friend_event_actor (actor_id, created_at),
  CONSTRAINT fk_friend_event_relation FOREIGN KEY (relation_id) REFERENCES lbn_friend_relation(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE lbn_user_block (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  blocker_id BIGINT NOT NULL,
  blocked_id BIGINT NOT NULL,
  reason     VARCHAR(255),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_user_block (blocker_id, blocked_id),
  KEY idx_blocked_user (blocked_id),
  CONSTRAINT fk_blocker FOREIGN KEY (blocker_id) REFERENCES lbn_user(id),
  CONSTRAINT fk_blocked FOREIGN KEY (blocked_id) REFERENCES lbn_user(id),
  CONSTRAINT ck_block_self CHECK (blocker_id <> blocked_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE lbn_conversation (
  id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
  friend_relation_id BIGINT NOT NULL,
  type               VARCHAR(16) NOT NULL DEFAULT 'PRIVATE',
  status             VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  last_message_id    BIGINT,
  last_message_at    DATETIME(3),
  created_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_private_friendship (friend_relation_id),
  KEY idx_conversation_last (last_message_at, id),
  CONSTRAINT fk_conversation_friend FOREIGN KEY (friend_relation_id) REFERENCES lbn_friend_relation(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE lbn_conversation_member (
  id                        BIGINT PRIMARY KEY AUTO_INCREMENT,
  conversation_id           BIGINT NOT NULL,
  user_id                   BIGINT NOT NULL,
  last_read_message_id      BIGINT,
  last_delivered_message_id BIGINT,
  unread_count              INT NOT NULL DEFAULT 0,
  muted                     TINYINT(1) NOT NULL DEFAULT 0,
  pinned                    TINYINT(1) NOT NULL DEFAULT 0,
  deleted_before_id         BIGINT,
  joined_at                 DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at                DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_conversation_user (conversation_id, user_id),
  KEY idx_member_user_recent (user_id, pinned, updated_at),
  CONSTRAINT fk_member_conversation FOREIGN KEY (conversation_id) REFERENCES lbn_conversation(id),
  CONSTRAINT fk_member_user FOREIGN KEY (user_id) REFERENCES lbn_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE lbn_message (
  id                BIGINT PRIMARY KEY AUTO_INCREMENT,
  conversation_id   BIGINT NOT NULL,
  sender_id          BIGINT NOT NULL,
  client_message_id VARCHAR(64) NOT NULL,
  message_type      VARCHAR(16) NOT NULL DEFAULT 'TEXT',
  content           TEXT NOT NULL,
  status            VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
  reply_to_id       BIGINT,
  created_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  edited_at         DATETIME(3),
  recalled_at       DATETIME(3),
  UNIQUE KEY uk_sender_client_message (sender_id, client_message_id),
  KEY idx_conversation_message (conversation_id, id),
  KEY idx_sender_time (sender_id, created_at),
  CONSTRAINT fk_message_conversation FOREIGN KEY (conversation_id) REFERENCES lbn_conversation(id),
  CONSTRAINT fk_message_sender FOREIGN KEY (sender_id) REFERENCES lbn_user(id),
  CONSTRAINT fk_message_reply FOREIGN KEY (reply_to_id) REFERENCES lbn_message(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE lbn_chat_outbox (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_id      VARCHAR(64) NOT NULL,
  event_type    VARCHAR(64) NOT NULL,
  aggregate_id  BIGINT,
  payload       JSON NOT NULL,
  status        VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  retry_count   INT NOT NULL DEFAULT 0,
  next_retry_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  last_error    VARCHAR(500),
  created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  published_at  DATETIME(3),
  UNIQUE KEY uk_outbox_event (event_id),
  KEY idx_outbox_pending (status, next_retry_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE lbn_user_chat_restriction (
  id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id             BIGINT NOT NULL,
  restriction_type    VARCHAR(32) NOT NULL,
  reason              VARCHAR(500) NOT NULL,
  starts_at           DATETIME(3) NOT NULL,
  ends_at             DATETIME(3),
  operator_admin_id   BIGINT NOT NULL,
  active              TINYINT(1) NOT NULL DEFAULT 1,
  created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_restriction_active (user_id, restriction_type, active, starts_at, ends_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE lbn_chat_report (
  id               BIGINT PRIMARY KEY AUTO_INCREMENT,
  reporter_id      BIGINT NOT NULL,
  reported_user_id BIGINT NOT NULL,
  message_id       BIGINT NOT NULL,
  report_type      VARCHAR(32) NOT NULL,
  description      VARCHAR(500),
  status           VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  handled_by       BIGINT,
  resolution       VARCHAR(500),
  created_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  handled_at       DATETIME(3),
  UNIQUE KEY uk_reporter_message (reporter_id, message_id),
  KEY idx_report_status (status, created_at),
  KEY idx_reported_user (reported_user_id, created_at),
  CONSTRAINT fk_report_message FOREIGN KEY (message_id) REFERENCES lbn_message(id),
  CONSTRAINT fk_reporter FOREIGN KEY (reporter_id) REFERENCES lbn_user(id),
  CONSTRAINT fk_reported_user FOREIGN KEY (reported_user_id) REFERENCES lbn_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
