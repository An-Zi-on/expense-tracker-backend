-- ============================================================
-- 增量脚本：新增移动端「看板」「账单」两个页面
--
-- 执行：mysql -uroot -p --default-character-set=utf8mb4 an_project < backend/sql/menu-expense-mobile.sql
--
-- 前端路由由 sys_menu 驱动，不插这两条记录，手机直接敲 /m/dashboard 会落到 404。
--
-- show_link = 0 是关键：路由照常注册，但不出现在 PC 侧边栏。
-- 移动端页面是独立布局（没有侧边栏/头部/页脚，底部换成 tab 导航），
-- 在 1440px 的 PC 上点开会很怪，所以只让手机通过 URL 访问。
--
-- 脚本可重复执行：按固定 id 覆盖，授权靠唯一索引去重。
-- ============================================================
USE an_project;
SET NAMES utf8mb4;

-- ---------------------------------------------
-- 1. 菜单记录（挂在「记账」分组 id=1 下）
-- ---------------------------------------------
INSERT INTO sys_menu
  (id, parent_id, path, name, component, title, icon, `rank`, menu_type, status,
   keep_alive, hidden_tag, fixed_tag, show_link, show_parent, remark)
VALUES
  (9, 1, '/m/dashboard', 'MobileExpenseDashboard', '@/views/expense/mobile/MobileDashboard.vue',
   '移动看板', 'DataAnalysis', 5, 0, 1, 0, 0, 0, 0, 0, '手机端看板，独立布局'),
  (10, 1, '/m/records', 'MobileExpenseRecordList', '@/views/expense/mobile/MobileRecordList.vue',
   '移动账单', 'List', 6, 0, 1, 0, 0, 0, 0, 0, '手机端账单列表，独立布局')
ON DUPLICATE KEY UPDATE
  parent_id   = VALUES(parent_id),
  path        = VALUES(path),
  name        = VALUES(name),
  component   = VALUES(component),
  title       = VALUES(title),
  icon        = VALUES(icon),
  `rank`      = VALUES(`rank`),
  menu_type   = VALUES(menu_type),
  status      = VALUES(status),
  show_link   = VALUES(show_link),
  show_parent = VALUES(show_parent),
  remark      = VALUES(remark);

-- ---------------------------------------------
-- 2. 授权给现有角色（按 role_key 匹配，不写死 role_id）
-- ---------------------------------------------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, 9 FROM sys_role r WHERE r.role_key IN ('user', 'admin')
ON DUPLICATE KEY UPDATE menu_id = VALUES(menu_id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, 10 FROM sys_role r WHERE r.role_key IN ('user', 'admin')
ON DUPLICATE KEY UPDATE menu_id = VALUES(menu_id);

-- ---------------------------------------------
-- 3. 自检：应查到 2 条菜单（show_link 均为 0）、至少 4 条授权
-- ---------------------------------------------
SELECT id, parent_id, path, title, show_link FROM sys_menu WHERE id IN (9, 10);
SELECT role_id, menu_id FROM sys_role_menu WHERE menu_id IN (9, 10);
