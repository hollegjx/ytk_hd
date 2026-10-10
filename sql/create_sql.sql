use tuku;

create table user
(
    userName     varchar(32)                        not null comment '用户名',
    userAccount  varchar(64)                        not null comment '用户账号',
    userPassword varchar(128)                       not null comment '用户密码',
    userAvatar   varchar(128)                       null comment '用户头像',
    userRole     int      default 0                 not null comment '用户角色',
    userProfile  varchar(128)                       null comment '用户简介',
    createTime   datetime default current_timestamp not null comment '创建时间',
    updateTime   datetime default current_timestamp null comment '更新时间',
    editTime     datetime default current_timestamp null comment '编辑时间',
    isDelete     tinyint  default 0                 not null comment '逻辑删除 0-未删除 1-已删除',
    id           BIGINT auto_increment comment '唯一id'
        primary key
)
    comment '用户表';

create unique index idx
    on user (userAccount)
    comment '根据用户的账号查询id';

create index user_userName_index
    on user (userName)
    comment '用户表的索引';

-- 图片表
create table if not exists picture
(
    id           bigint auto_increment comment 'id' primary key,
    url          varchar(512)                           not null comment '主展示图 URL；派生图未生成时回退为原图',
    originalUrl  varchar(512)                           not null comment 'COS 原始文件 URL，用于重新处理和下载',
    thumbnailUrl varchar(512)                           null comment '列表缩略图 URL；为空时前端回退使用 url',
    sourceUrl    varchar(1024)                          null comment '外部图片来源 URL，仅用于追踪来源',
    name         varchar(128)                           not null comment '图片名称',
    introduction varchar(512)                           null comment '简介',
    category     varchar(64)                            null comment '分类',
    tags         varchar(512)                           null comment '标签（JSON 数组）',
    picSize      bigint                                 null comment '图片体积',
    picWidth     int                                    null comment '图片宽度',
    picHeight    int                                    null comment '图片高度',
    picScale     double                                 null comment '图片宽高比例',
    picFormat    varchar(32)                            null comment '图片格式',
    userId       bigint                                 not null comment '创建用户 id',
    uploadStatus tinyint  default 0                    not null comment '上传状态：0-草稿；1-已完成',
    reviewStatus int      default 0                    not null comment '审核状态：0-待审核；1-通过；2-拒绝',
    reviewMessage varchar(512)                          null comment '审核信息',
    reviewerId   bigint                                 null comment '审核人 ID',
    reviewTime   datetime                               null comment '审核时间',
    createTime   datetime default CURRENT_TIMESTAMP    not null comment '创建时间',
    editTime     datetime default CURRENT_TIMESTAMP    not null comment '编辑时间',
    updateTime   datetime default CURRENT_TIMESTAMP    not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint  default 0                    not null comment '是否删除',
    INDEX idx_name (name),                -- 提升基于图片名称的查询性能
    INDEX idx_introduction (introduction), -- 用于模糊搜索图片简介
    INDEX idx_category (category),         -- 提升基于分类的查询性能
    INDEX idx_tags (tags),                 -- 提升基于标签的查询性能
    INDEX idx_userId (userId),             -- 提升基于用户 ID 的查询性能
    INDEX idx_uploadStatus (uploadStatus), -- 提升草稿清理和已完成图片查询性能
    INDEX idx_reviewStatus (reviewStatus)  -- 提升基于审核状态的查询性能
) comment '图片' collate = utf8mb4_unicode_ci;

-- 公开图片列表缓存的持久化失效版本
CREATE TABLE IF NOT EXISTS picture_list_revision (
    id TINYINT NOT NULL PRIMARY KEY,
    revision BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB COMMENT='公开图片列表缓存版本';
INSERT IGNORE INTO picture_list_revision (id, revision) VALUES (1, 0);
