package anzihe.com.common_template.service.impl;

import anzihe.com.common_template.common.UserConstant;
import anzihe.com.common_template.exception.ErrorCode;
import anzihe.com.common_template.utils.ThrowUtils;
import anzihe.com.common_template.mapper.UserMapper;
import anzihe.com.common_template.model.DTO.user.UserQueryRequest;
import anzihe.com.common_template.model.VO.user.LoginUserVO;
import anzihe.com.common_template.model.VO.user.UserVO;
import anzihe.com.common_template.model.entity.User;
import anzihe.com.common_template.model.entity.SysRole;
import anzihe.com.common_template.model.enums.UserRoleEnum;
import anzihe.com.common_template.service.SysRoleService;
import anzihe.com.common_template.service.UserService;
import anzihe.com.common_template.utils.TokenUtil;
import anzihe.com.common_template.utils.TokenBlacklist;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    /** 允许排序的驼峰字段 -> 实际列名。只放行这里列出的，其余一律忽略 */
    private static final Map<String, String> SORTABLE_COLUMNS = Map.of(
            "id", "id",
            "userAccount", "user_account",
            "userName", "user_name",
            "userRole", "user_role",
            "roleId", "role_id",
            "createTime", "create_time",
            "updateTime", "update_time",
            "editTime", "edit_time"
    );

    @Resource
    private UserMapper userMapper;

    @Resource
    private TokenBlacklist tokenBlacklist;

    @Resource
    private SysRoleService sysRoleService;

    @Override
    public void register(String userAccount, String password, String checkPassword) {
        ThrowUtils.throwException(userAccount.length() < 8, ErrorCode.PARAMS_ERROR, "账号长度大于8位");
        ThrowUtils.throwException(password.length() < 8 || checkPassword.length() < 8, ErrorCode.PARAMS_ERROR, "密码长度大于8位");
        ThrowUtils.throwException(!password.equals(checkPassword), ErrorCode.PARAMS_ERROR, "输入密码不一致");
        // 注意：QueryWrapper 的列名不会做驼峰转换，必须与表字段一致（user_account）
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_account", userAccount);
        long count = this.count(queryWrapper);
        ThrowUtils.throwException(count > 0, ErrorCode.PARAMS_ERROR, "账号已存在");
        String encryptPassword = getEncryptPassword(password);
        User updateUser = new User();
        updateUser.setUserAccount(userAccount);
        updateUser.setUserPassword(encryptPassword);
        updateUser.setUserName("无名");
        updateUser.setUserRole(UserRoleEnum.USER.getValue());
        // 注册一律落到「普通用户」角色：只写 user_role 会让 role_id 为空，
        // 菜单是按 role_id 查的，结果就是新用户登录后侧边栏空空如也
        SysRole defaultRole = sysRoleService.getOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleKey, UserRoleEnum.USER.getValue())
                .eq(SysRole::getDeleted, 0)
                .orderByAsc(SysRole::getId)
                .last("LIMIT 1"));
        if (defaultRole != null) {
            updateUser.setRoleId(defaultRole.getId().intValue());
        }
        long result = userMapper.insert(updateUser);
        ThrowUtils.throwException(result <= 0, ErrorCode.SYSTEM_ERROR);
    }

    @Override
    public String userLogin(String userAccount, String password, HttpServletRequest request) {
        ThrowUtils.throwException(userAccount.length() < 8 || StrUtil.isEmptyIfStr(userAccount), ErrorCode.PARAMS_ERROR);
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_account", userAccount);
        User selectUser = userMapper.selectOne(queryWrapper);
        ThrowUtils.throwException(selectUser == null, ErrorCode.PARAMS_ERROR, "用户不存在");
        String userPassword = selectUser.getUserPassword();
        ThrowUtils.throwException(!userPassword.equals(getEncryptPassword(password)), ErrorCode.PARAMS_ERROR, "密码错误");
        UserVO loginUserVO = toUserVO(selectUser);
        return TokenUtil.sign(loginUserVO);
    }

    @Override
    public UserVO currentUser(HttpServletRequest request) {
        String token = request.getHeader("token");
        ThrowUtils.throwException(!TokenUtil.verify(token), ErrorCode.NOT_LOGIN_ERROR);
        UserVO currentUser = TokenUtil.getCurrentUser(token);
        ThrowUtils.throwException(currentUser == null, ErrorCode.NOT_LOGIN_ERROR);
        User selectUser = userMapper.selectById(currentUser.getId());
        UserVO loginUserVO = toUserVO(selectUser);
        // 刷新 token，并通过响应头回传给前端
        String newToken = TokenUtil.refreshToken(token);
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null && attributes.getResponse() != null) {
            attributes.getResponse().setHeader("token", newToken);
        }
        return loginUserVO;
    }

    @Override
    public boolean loginOutUser(HttpServletRequest request) {
        // JWT 是无状态的，服务端原本没有任何办法让已发出的 token 失效：
        // 前端清掉 localStorage 只是「看不见了」，抓包拿旧 token 还能继续用满 30 分钟。
        // 这里把当前 token 登记进黑名单，之后 TokenUtil.verify 会直接拒绝，
        // 所有走 verify 的接口都会返回 40100，前端自动跳回登录页。
        String token = request.getHeader("token");
        tokenBlacklist.revoke(token);
        return true;
    }

    @Override
    public String getEncryptPassword(String text) {
        return SecureUtil.md5(text + UserConstant.USER_PASSWORD_SALT);
    }

    @Override
    public LoginUserVO toLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO bean = BeanUtil.toBean(user, LoginUserVO.class);
        bean.setCreateTime(LocalDateTimeUtil.of(user.getCreateTime()));
        bean.setUpdateTime(LocalDateTimeUtil.of(user.getUpdateTime()));
        return bean;
    }

    @Override
    public UserVO toUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = BeanUtil.toBean(user, UserVO.class);
        if (user.getCreateTime() != null) {
            userVO.setCreateTime(java.util.Date.from(user.getCreateTime().atZone(java.time.ZoneId.systemDefault()).toInstant()));
        }
        if (user.getUpdateTime() != null) {
            userVO.setUpdateTime(java.util.Date.from(user.getUpdateTime().atZone(java.time.ZoneId.systemDefault()).toInstant()));
        }
        return userVO;
    }

    @Override
    public List<UserVO> toListUserVO(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream().map(this::toUserVO).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper<User> getUserQueryWrapper(UserQueryRequest queryRequest) {
        ThrowUtils.throwException(queryRequest == null, ErrorCode.OPERATION_ERROR, "查询条件为空");
        Long id = queryRequest.getId();
        String userName = queryRequest.getUserName();
        String userAccount = queryRequest.getUserAccount();
        String userProfile = queryRequest.getUserProfile();
        String userRole = queryRequest.getUserRole();
        String sortField = queryRequest.getSortField();
        String sortOrder = queryRequest.getSortOrder();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        // 注意：QueryWrapper 传字符串列名不做驼峰转换，必须写表字段。
        // 这里原来写的是 "userAccount" / "userName" / "userRole" / "userProfile"，
        // 生成的 SQL 就是 WHERE userAccount LIKE ?，MySQL 报 Unknown column，
        // 管理员在「角色与用户」里一搜用户就直接 500。
        queryWrapper.eq(Objects.nonNull(id), "id", id);
        queryWrapper.eq(StrUtil.isNotBlank(userRole), "user_role", userRole);
        queryWrapper.like(StrUtil.isNotBlank(userAccount), "user_account", userAccount);
        queryWrapper.like(StrUtil.isNotBlank(userName), "user_name", userName);
        queryWrapper.like(StrUtil.isNotBlank(userProfile), "user_profile", userProfile);
        // 排序字段来自前端，必须走白名单：既避免拼进不存在的列，也挡掉 SQL 注入。
        // 另外 sortOrder 显式传 null 时不能直接 .equals()，那会 NPE。
        String sortColumn = SORTABLE_COLUMNS.get(StrUtil.trimToEmpty(sortField));
        if (sortColumn != null) {
            queryWrapper.orderBy(true, "ascend".equals(sortOrder), sortColumn);
        }
        return queryWrapper;
    }

    @Override
    public boolean isAdmin(LoginUserVO userVO) {
        return userVO != null && UserRoleEnum.ADMIN.equals(UserRoleEnum.getEnumByValue(userVO.getUserRole()));
    }
}
