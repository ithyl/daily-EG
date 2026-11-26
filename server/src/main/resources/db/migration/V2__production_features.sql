-- Additive migration; preserves all eight imported lessons and user progress.
ALTER TABLE sf_lesson ADD COLUMN generation_source VARCHAR(20) NOT NULL DEFAULT 'IMPORT',
  ADD COLUMN published_at DATETIME(3) NULL;
ALTER TABLE sf_exercise ADD COLUMN hint_en TEXT NULL,
  ADD COLUMN keywords_json JSON NULL;
ALTER TABLE sf_speaking_attempt ADD COLUMN input_type VARCHAR(12) NOT NULL DEFAULT 'TEXT',
  ADD COLUMN attempt_no INT NOT NULL DEFAULT 1;
ALTER TABLE sf_plan ADD COLUMN generation_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  ADD COLUMN target_level VARCHAR(12) NOT NULL DEFAULT 'B1+',
  ADD COLUMN publish_time TIME NOT NULL DEFAULT '07:30:00';

CREATE TABLE sf_generation_job (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  plan_id BIGINT UNSIGNED NOT NULL,
  lesson_date DATE NOT NULL,
  idempotency_key VARCHAR(150) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
  provider VARCHAR(40) NOT NULL,
  message VARCHAR(800) NULL,
  started_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  finished_at DATETIME(3) NULL,
  UNIQUE KEY uk_generation_job_key (idempotency_key),
  KEY idx_job_plan_date (plan_id, lesson_date),
  CONSTRAINT fk_generation_plan FOREIGN KEY (plan_id) REFERENCES sf_plan(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sf_audio_asset (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  lesson_id BIGINT UNSIGNED NOT NULL,
  segment_id BIGINT UNSIGNED NULL,
  cache_key CHAR(64) NOT NULL,
  storage_path VARCHAR(500) NOT NULL,
  mime_type VARCHAR(40) NOT NULL DEFAULT 'audio/mpeg',
  bytes_size BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_audio_cache_key (cache_key),
  KEY idx_audio_lesson (lesson_id,segment_id),
  CONSTRAINT fk_audio_lesson FOREIGN KEY (lesson_id) REFERENCES sf_lesson(id) ON DELETE CASCADE,
  CONSTRAINT fk_audio_segment FOREIGN KEY (segment_id) REFERENCES sf_lesson_segment(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sf_pronunciation_tip (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  lesson_id BIGINT UNSIGNED NOT NULL,
  sort_order INT NOT NULL,
  target_text VARCHAR(500) NOT NULL,
  ipa_us VARCHAR(500) NULL,
  explanation_zh TEXT NOT NULL,
  example_en TEXT NULL,
  UNIQUE KEY uk_tip_sort (lesson_id,sort_order),
  CONSTRAINT fk_tip_lesson FOREIGN KEY (lesson_id) REFERENCES sf_lesson(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
