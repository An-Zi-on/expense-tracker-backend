-- ============================================================
-- 记账模块建表脚本
-- 依赖：an_project 库（先在 sql/sql.sql 建好基础表）
-- 执行：mysql -uroot -p --default-character-set=utf8mb4 an_project < sql/expense.sql
-- ============================================================
SET NAMES utf8mb4;

-- ----------------------------
-- 账单表
-- 设计要点：
-- 1) 主键用客户端生成的 UUID（VARCHAR(36)），不用自增。
--    这样手机离线补传、脚本重试都能安全重放：同一个 id 插两次只会成功一次（幂等）。
-- 2) 金额一律存「分」（BIGINT），避免浮点误差；前端负责元 -> 分的换算。
-- 3) user_id 是数据隔离的唯一依据，所有查询都必须带上，且只能取自 token。
-- ----------------------------
CREATE TABLE IF NOT EXISTS `expense_record`
(
    `id`           varchar(36) NOT NULL COMMENT '主键（客户端 UUID，幂等键）',
    `user_id`      bigint      NOT NULL COMMENT '所属用户 ID',
    `record_date`  date        NOT NULL COMMENT '账单日期',
    `amount_cents` bigint      NOT NULL COMMENT '金额（分）',
    `expense_type` varchar(16) NOT NULL DEFAULT 'daily' COMMENT '支出类型：daily=日常开销 site=网站开发',
    `category`     varchar(32) NOT NULL COMMENT '分类：餐饮/交通/…；网站开发：域名/服务器/…',
    `project`      varchar(64)          DEFAULT NULL COMMENT '项目名称（可选）',
    `payment`      varchar(16)          DEFAULT NULL COMMENT '支付方式：微信/支付宝/银行卡/现金/其他',
    `note`         varchar(255)         DEFAULT NULL COMMENT '备注',
    `source`       varchar(16) NOT NULL DEFAULT 'manual' COMMENT '来源：manual=手动 import=CSV 导入 receipt=小票识别',
    `create_time`  datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_delete`    tinyint     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 1 删除',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_user_date` (`user_id`, `record_date` DESC, `id`) USING BTREE,
    KEY `idx_user_type_date` (`user_id`, `expense_type`, `record_date` DESC) USING BTREE,
    KEY `idx_user_category` (`user_id`, `category`) USING BTREE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='记账流水';

-- ----------------------------
-- 预算表（每个用户一行）
-- 项目名不叫 budget 是因为 budget 在部分 MySQL 版本里是保留字（Oracle 模式）。
-- ----------------------------
CREATE TABLE IF NOT EXISTS `expense_budget`
(
    `id`                  bigint   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`             bigint   NOT NULL COMMENT '所属用户 ID',
    `monthly_total_cents` bigint   NOT NULL DEFAULT 0 COMMENT '每月总预算（分），0 表示不设置',
    `monthly_site_cents`  bigint   NOT NULL DEFAULT 0 COMMENT '每月网站开发预算（分），0 表示不设置',
    `create_time`         datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`         datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_user` (`user_id`) USING BTREE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='记账预算设置';
