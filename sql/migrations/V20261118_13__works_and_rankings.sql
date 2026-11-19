-- 目的：共享作品、榜单及个人记录的作品关联。
-- 前置：PostgreSQL 18+，已执行此前增量。
-- 影响：新增三张公共表和两个可空作品引用。
-- 重跑：Flyway 版本控制，失败事务回滚。
-- 执行：通过独立 Flyway migrate 执行。

CREATE TABLE creative_work (
  id bigint PRIMARY KEY,
  kind varchar(16) NOT NULL,
  title varchar(255) NOT NULL,
  director varchar(100),
  cast_names jsonb,
  author varchar(100),
  duration_minutes integer,
  episode_count integer,
  page_count integer,
  platform varchar(32),
  resource_type varchar(32),
  external_key varchar(255),
  cover_url varchar(1000),
  metadata_ready_time timestamp(0) without time zone,
  create_user bigint,
  create_time timestamp(0) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_user bigint,
  update_time timestamp(0) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_deleted smallint NOT NULL DEFAULT 0,
  CONSTRAINT ck_creative_work_kind CHECK (kind IN ('film','series','book')),
  CONSTRAINT ck_creative_work_identity CHECK (num_nonnulls(platform,resource_type,external_key) IN (0,3)),
  CONSTRAINT ck_creative_work_duration CHECK (duration_minutes > 0),
  CONSTRAINT ck_creative_work_episodes CHECK (episode_count > 0),
  CONSTRAINT ck_creative_work_pages CHECK (page_count > 0),
  CONSTRAINT ck_creative_work_cast CHECK (jsonb_typeof(cast_names) = 'array'),
  CONSTRAINT ck_creative_work_active CHECK (is_deleted IN (0,1))
);

COMMENT ON TABLE creative_work IS '共享作品';

COMMENT ON COLUMN creative_work.id IS '作品主键，由应用生成雪花 ID';
COMMENT ON COLUMN creative_work.kind IS '作品类型，首期为 film、series、book';
COMMENT ON COLUMN creative_work.title IS '作品显示名称，允许同名';
COMMENT ON COLUMN creative_work.director IS '影视导演，优先保存来源提供的主要导演，供现有表单回填';
COMMENT ON COLUMN creative_work.cast_names IS '影视演员姓名数组，按来源顺序保存，未取得时为空';
COMMENT ON COLUMN creative_work.author IS '图书作者，供现有表单回填';
COMMENT ON COLUMN creative_work.duration_minutes IS '电影总时长，单位为分钟';
COMMENT ON COLUMN creative_work.episode_count IS '剧集总集数';
COMMENT ON COLUMN creative_work.page_count IS '该图书版本的总页数';
COMMENT ON COLUMN creative_work.platform IS '外部系统，例如 douban';
COMMENT ON COLUMN creative_work.resource_type IS '外部条目类型，例如 movie_subject、book_subject';
COMMENT ON COLUMN creative_work.external_key IS '外部作品条目 ID，按字符串保存';
COMMENT ON COLUMN creative_work.cover_url IS '校验后的原始封面 URL，用于服务端推导图片缓存键';
COMMENT ON COLUMN creative_work.metadata_ready_time IS '详情确认并保存的时间。仅有榜单基础资料时为空';
COMMENT ON COLUMN creative_work.create_user IS '创建操作者 ID，系统任务可留空';
COMMENT ON COLUMN creative_work.create_time IS '创建时间';
COMMENT ON COLUMN creative_work.update_user IS '最后更新操作者 ID，系统任务可留空';
COMMENT ON COLUMN creative_work.update_time IS '最后更新时间';
COMMENT ON COLUMN creative_work.is_deleted IS '逻辑删除标记，0 有效，1 已删除';

CREATE TABLE ranking_list (
  id bigint PRIMARY KEY,
  name varchar(120) NOT NULL,
  publisher_code varchar(32) COLLATE "C" NOT NULL,
  list_key varchar(64) COLLATE "C" NOT NULL,
  edition_key varchar(32) COLLATE "C" NOT NULL,
  source_url varchar(1000),
  last_sync_time timestamp(0) without time zone,
  create_user bigint,
  create_time timestamp(0) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_user bigint,
  update_time timestamp(0) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_deleted smallint NOT NULL DEFAULT 0,
  CONSTRAINT ck_ranking_list_active CHECK (is_deleted IN (0,1))
);

COMMENT ON TABLE ranking_list IS '榜单期次';

COMMENT ON COLUMN ranking_list.id IS '榜单主键，由应用生成雪花 ID';
COMMENT ON COLUMN ranking_list.name IS '前端直接显示的榜单名称，可以改名';
COMMENT ON COLUMN ranking_list.publisher_code IS '发布方编码，例如 douban';
COMMENT ON COLUMN ranking_list.list_key IS '发布方下的稳定榜单标识，例如 movie_top250、book_top250';
COMMENT ON COLUMN ranking_list.edition_key IS '榜单期次，滚动榜显式填 current，年度榜可填 2026';
COMMENT ON COLUMN ranking_list.source_url IS '对应期次的原始榜单页面';
COMMENT ON COLUMN ranking_list.last_sync_time IS '最近一次完整同步成功的时间，尚未同步时为空';
COMMENT ON COLUMN ranking_list.create_user IS '创建操作者 ID，系统任务可留空';
COMMENT ON COLUMN ranking_list.create_time IS '创建时间';
COMMENT ON COLUMN ranking_list.update_user IS '最后更新操作者 ID，系统任务可留空';
COMMENT ON COLUMN ranking_list.update_time IS '最后更新时间';
COMMENT ON COLUMN ranking_list.is_deleted IS '逻辑删除标记，0 有效，1 已删除';

CREATE TABLE ranking_list_item (
  id bigint PRIMARY KEY,
  ranking_list_id bigint NOT NULL,
  creative_work_id bigint NOT NULL,
  rank integer,
  sort_order integer NOT NULL,
  create_user bigint,
  create_time timestamp(0) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_user bigint,
  update_time timestamp(0) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_deleted smallint NOT NULL DEFAULT 0,
  CONSTRAINT ck_ranking_list_item_rank CHECK (rank > 0),
  CONSTRAINT ck_ranking_list_item_sort_order CHECK (sort_order >= 0),
  CONSTRAINT ck_ranking_list_item_active CHECK (is_deleted IN (0,1))
);

COMMENT ON TABLE ranking_list_item IS '作品入榜名次';

COMMENT ON COLUMN ranking_list_item.id IS '榜单条目主键，由应用生成雪花 ID';
COMMENT ON COLUMN ranking_list_item.ranking_list_id IS '关联 ranking_list.id';
COMMENT ON COLUMN ranking_list_item.creative_work_id IS '关联入榜作品 creative_work.id';
COMMENT ON COLUMN ranking_list_item.rank IS '正整数名次，允许并列。空值表示只入选';
COMMENT ON COLUMN ranking_list_item.sort_order IS '从 0 开始的来源顺序，用于并列和无名次条目';
COMMENT ON COLUMN ranking_list_item.create_user IS '创建操作者 ID，系统任务可留空';
COMMENT ON COLUMN ranking_list_item.create_time IS '创建时间';
COMMENT ON COLUMN ranking_list_item.update_user IS '最后更新操作者 ID，系统任务可留空';
COMMENT ON COLUMN ranking_list_item.update_time IS '最后更新时间';
COMMENT ON COLUMN ranking_list_item.is_deleted IS '逻辑删除标记，0 有效，1 已掉榜或已移出';

CREATE UNIQUE INDEX uk_creative_work_identity ON creative_work(platform, resource_type, external_key) WHERE external_key IS NOT NULL;
CREATE UNIQUE INDEX uk_ranking_list_edition ON ranking_list(publisher_code, list_key, edition_key);
CREATE UNIQUE INDEX uk_ranking_list_item_work ON ranking_list_item(ranking_list_id, creative_work_id);
CREATE INDEX idx_ranking_list_item_order ON ranking_list_item(ranking_list_id, rank NULLS LAST, sort_order, id) WHERE is_deleted = 0;
CREATE INDEX idx_ranking_list_item_work ON ranking_list_item(creative_work_id, ranking_list_id) WHERE is_deleted = 0;

ALTER TABLE movie ADD COLUMN creative_work_id bigint;
COMMENT ON COLUMN movie.creative_work_id IS '共享作品ID，未识别的记录为空';
CREATE INDEX idx_movie_work ON movie(user_id, creative_work_id) WHERE creative_work_id IS NOT NULL AND is_deleted = 0;

ALTER TABLE read_record ADD COLUMN creative_work_id bigint;
COMMENT ON COLUMN read_record.creative_work_id IS '共享作品ID，未识别的记录为空';
CREATE INDEX idx_read_record_work ON read_record(user_id, creative_work_id) WHERE creative_work_id IS NOT NULL AND is_deleted = 0;

-- 历史个人资料可能经过编辑。这里只提取来源身份，以条目号暂代作品名。
WITH identities AS (
  SELECT 'movie_subject' AS resource_type, 'film' AS kind,
    substring(trim(url) FROM '(?i)^https?://movie[.]douban[.]com/subject/([0-9]+)/?([?#].*)?$') AS subject FROM movie
  UNION
  SELECT 'book_subject', 'book',
    substring(trim(url) FROM '(?i)^https?://book[.]douban[.]com/subject/([0-9]+)/?([?#].*)?$') FROM read_record
)
INSERT INTO creative_work(id, platform, resource_type, external_key, kind, title)
SELECT 245000000000000000 + row_number() OVER (ORDER BY resource_type, subject),
       'douban', resource_type, subject, kind, subject
FROM identities WHERE subject IS NOT NULL AND length(subject) <= 255;
