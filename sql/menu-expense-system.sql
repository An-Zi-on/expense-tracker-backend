-- ============================================================
-- 增量脚本：新增「记账 → 系统设置」菜单
--
-- 执行：mysql -uroot -p --default-character-set=utf8mb4 an_project < backend/sql/menu-expense-system.sql
--
-- 为什么需要这个脚本：前端路由完全由 sys_menu 驱动（登录后拉 /api/menu/user_tree
-- 再注册路由），不往表里插这条记录，直接敲 /expense/system 只会落到 404。
-- 脚本可重复执行：菜单按固定 id 8 覆盖，角色授权靠唯一索引去重。
-- ============================================================
USE an_project;
SET NAMES utf8mb4;

-- ---------------------------------------------
-- 1. 菜单记录（挂在「记账」分组 id=1 下，排第 4）
-- ---------------------------------------------
INSERT INTO sys_menu
  (id, parent_id, path, name, component, title, icon, `rank`, menu_type, status,
   keep_alive, hidden_tag, fixed_tag, show_link, show_parent, remark)
VALUES
  (8, 1, '/expense/system', 'ExpenseSystem', '@/views/expense/System.vue',
   '系统设置', 'Tools', 4, 0, 1, 0, 0, 0, 1, 0, '数据初始化与清空')
ON DUPLICATE KEY UPDATE
  parent_id  = VALUES(parent_id),
  path       = VALUES(path),
  name       = VALUES(name),
  component  = VALUES(component),
  title      = VALUES(title),
  icon       = VALUES(icon),
  `rank`     = VALUES(`rank`),
  menu_type  = VALUES(menu_type),
  status     = VALUES(status),
  show_link  = VALUES(show_link),
  show_parent= VALUES(show_parent),
  remark     = VALUES(remark);

-- ---------------------------------------------
-- 2. 授权给现有角色（普通用户 + 管理员都能用，只操作自己的数据）
--    不写死 role_id：按 role_key 匹配，避免库里的角色 id 与脚本假设不一致时踩外键
-- ---------------------------------------------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, 8 FROM sys_role r WHERE r.role_key IN ('user', 'admin')
ON DUPLICATE KEY UPDATE menu_id = VALUES(menu_id);

-- ---------------------------------------------
-- 3. 自检：应能查到 1 条菜单、至少 2 条授权
-- ---------------------------------------------
SELECT id, parent_id, path, title, icon FROM sys_menu WHERE id = 8;
SELECT role_id, menu_id FROM sys_role_menu WHERE menu_id = 8;
