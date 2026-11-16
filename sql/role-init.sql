/*
 * 记账系统的角色与菜单初始化（两角色：管理员 / 普通用户）
 *
 * 执行方式：
 *   mysql -uroot -p an_project < backend/sql/role-init.sql
 *
 * 脚本可重复执行：所有 id 都是固定的，先按 id 清理再写入，
 * 不会因为多次执行而出现重复角色或重复菜单。
 *
 * 角色约定：
 *   id=1 普通用户 role_key=user   → 只看到「记账」分组（看板 / 明细 / 预算）
 *   id=2 管理员   role_key=admin  → 记账 + 系统管理（角色与用户 / 菜单管理）
 *
 * 注意：user.user_role 这个字段是 @AuthCheck 鉴权用的值，
 * 必须和 sys_role.role_key 保持一致（脚本里一起同步）。
 */

USE an_project;
SET NAMES utf8mb4;

-- ---------------------------------------------
-- 1. 清理本脚本管理的角色与菜单（1、2 号角色；1~8 号菜单）
--    sys_role_menu / sys_user_menu 有外键 ON DELETE CASCADE，会跟着清掉
-- ---------------------------------------------
DELETE FROM sys_role WHERE id IN (1, 2);
DELETE FROM sys_menu WHERE id BETWEEN 1 AND 8;

-- ---------------------------------------------
-- 2. 两种角色
-- ---------------------------------------------
INSERT INTO sys_role (id, role_name, role_key, remark, deleted) VALUES
  (1, '普通用户', 'user',  '只能使用记账功能，账单数据仅本人可见', 0),
  (2, '管理员',   'admin', '拥有全部菜单，可管理角色、用户与菜单', 0);

-- ---------------------------------------------
-- 3. 菜单
--    component 为空 = 目录节点（前端用透传 RouterView 承载子路由）
--    component 路径以 @/ 开头，前端按 /src/ 解析
--    icon 用 Element Plus 图标名，MenuTree 里做映射
-- ---------------------------------------------
INSERT INTO sys_menu
  (id, parent_id, path, name, component, title, icon, `rank`, menu_type, status,
   keep_alive, hidden_tag, fixed_tag, show_link, show_parent, remark)
VALUES
  -- 记账分组
  (1, 0, '/expense', 'Expense', NULL,
   '记账', 'Wallet', 10, 0, 1, 0, 0, 0, 1, 0, '记账功能分组'),
  (2, 1, '/expense/dashboard', 'ExpenseDashboard', '@/views/expense/Dashboard.vue',
   '记账看板', 'DataAnalysis', 1, 0, 1, 0, 0, 0, 1, 0, NULL),
  (3, 1, '/expense/records', 'ExpenseRecords', '@/views/expense/RecordList.vue',
   '账单明细', 'List', 2, 0, 1, 0, 0, 0, 1, 0, NULL),
  (4, 1, '/expense/settings', 'ExpenseSettings', '@/views/expense/Settings.vue',
   '预算与数据', 'Money', 3, 0, 1, 0, 0, 0, 1, 0, NULL),
  (8, 1, '/expense/system', 'ExpenseSystem', '@/views/expense/System.vue',
   '系统设置', 'Tools', 4, 0, 1, 0, 0, 0, 1, 0, '数据初始化与清空'),

  -- 系统管理分组（仅管理员）
  (5, 0, '/system', 'System', NULL,
   '系统管理', 'Setting', 90, 0, 1, 0, 0, 0, 1, 0, '管理员专用'),
  (6, 5, '/system/role', 'SystemRole', '@/views/system/role/list.vue',
   '角色与用户', 'User', 1, 0, 1, 0, 0, 0, 1, 0, '角色维护 + 给用户分配角色'),
  (7, 5, '/system/menu', 'SystemMenu', '@/views/system/menu/list.vue',
   '菜单管理', 'Menu', 2, 0, 1, 0, 0, 0, 1, 0, '菜单与路由维护');

-- ---------------------------------------------
-- 4. 角色 → 菜单授权
--    普通用户：1~4、8（记账全部分组，含系统设置）
--    管理员：1~8（全部）
-- ---------------------------------------------
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
  (1, 1), (1, 2), (1, 3), (1, 4), (1, 8),
  (2, 1), (2, 2), (2, 3), (2, 4), (2, 8), (2, 5), (2, 6), (2, 7);

-- ---------------------------------------------
-- 5. 用户 → 角色
--    默认所有人先落到「普通用户」，再把指定账号提成管理员。
--    改这里的管理员账号名即可换人。
-- ---------------------------------------------
UPDATE `user` SET role_id = 1, user_role = 'user' WHERE role_id IS NULL OR role_id NOT IN (1, 2);
UPDATE `user` SET role_id = 2, user_role = 'admin' WHERE user_account = 'anzihe_test';
