package anzihe.com.common_template.aop;

import anzihe.com.common_template.annotation.AuthCheck;
import anzihe.com.common_template.exception.BusinessException;
import anzihe.com.common_template.exception.ErrorCode;
import anzihe.com.common_template.model.VO.user.UserVO;
import anzihe.com.common_template.model.entity.User;
import anzihe.com.common_template.model.enums.UserRoleEnum;
import anzihe.com.common_template.service.UserService;
import anzihe.com.common_template.utils.TokenUtil;
import cn.hutool.core.util.StrUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 角色鉴权切面：方法上标了 @AuthCheck(mustRole = "admin") 才拦截，没标就直接放行。
 *
 * 角色以数据库 user.user_role 为准，token 里的角色只作为兜底：
 * token 有效期 30 分钟，如果只看 token，把某人降级成普通用户后他还能拿着旧 token
 * 继续用管理员接口，撤销权限等于没生效。
 */
@Aspect
@Component
public class AuthInterceptor {

    @Resource
    private UserService userService;

    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        UserRoleEnum mustRole = UserRoleEnum.getEnumByValue(authCheck.mustRole());
        // 没要求角色：登录校验交给各接口自己（或 TokenUtil），这里直接放行
        if (mustRole == null) {
            return joinPoint.proceed();
        }

        UserRoleEnum currentRole = resolveCurrentRole();
        // 管理员拥有一切权限；其余角色必须与注解要求一致
        if (!UserRoleEnum.ADMIN.equals(currentRole) && !mustRole.equals(currentRole)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限，该操作仅管理员可用");
        }
        return joinPoint.proceed();
    }

    /**
     * 取当前登录用户的角色：先查库，查不到再退回 token 里的快照
     */
    private UserRoleEnum resolveCurrentRole() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "未登录");
        }
        HttpServletRequest request = servletRequestAttributes.getRequest();
        String token = request.getHeader("token");
        if (StrUtil.isBlank(token) || !TokenUtil.verify(token)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "登录已过期，请重新登录");
        }
        UserVO tokenUser = TokenUtil.getCurrentUser(token);

        String roleValue = tokenUser.getUserRole();
        User dbUser = tokenUser.getId() == null ? null : userService.getById(tokenUser.getId());
        if (dbUser != null && StrUtil.isNotBlank(dbUser.getUserRole())) {
            roleValue = dbUser.getUserRole();
        }
        return UserRoleEnum.getEnumByValue(roleValue);
    }
}
