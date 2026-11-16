package anzihe.com.common_template.controller.system;

import anzihe.com.common_template.annotation.AuthCheck;
import anzihe.com.common_template.common.BaseResponse;
import anzihe.com.common_template.common.DeleteRequest;
import anzihe.com.common_template.common.ResultUtils;
import anzihe.com.common_template.common.UserConstant;
import anzihe.com.common_template.exception.ErrorCode;
import anzihe.com.common_template.model.DTO.menu.SysMenuDTO;
import anzihe.com.common_template.model.VO.menu.SysMenuTreeVO;
import anzihe.com.common_template.model.VO.menu.SysMenuVO;
import anzihe.com.common_template.model.VO.user.UserVO;
import anzihe.com.common_template.service.SysMenuService;
import anzihe.com.common_template.utils.ThrowUtils;
import anzihe.com.common_template.utils.TokenUtil;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 菜单权限接口
 *
 * 增删改与全量树仅管理员可用；user_tree 是登录用户拉自己的菜单，任何人都能调，
 * 但非管理员只能查自己（否则改个 userId 就能看到别人的菜单）。
 */
@RestController
@RequestMapping("/menu")
public class MenuController {

    @Resource
    private SysMenuService menuService;

    /**
     * 新增菜单
     */
    @PostMapping("/save")
    @AuthCheck(mustRole = UserConstant.USER_ADMIN)
    public BaseResponse<Void> save(@RequestBody SysMenuDTO dto) {
        menuService.save(dto);
        return ResultUtils.success(null);
    }

    /**
     * 修改菜单
     */
    @PutMapping("/update")
    @AuthCheck(mustRole = UserConstant.USER_ADMIN)
    public BaseResponse<Void> update(@RequestBody SysMenuDTO dto) {
        menuService.update(dto);
        return ResultUtils.success(null);
    }

    /**
     * 删除菜单
     */
    @DeleteMapping("/delete/{id}")
    @AuthCheck(mustRole = UserConstant.USER_ADMIN)
    public BaseResponse<Void> delete(DeleteRequest deleteRequest) {
        menuService.delete(deleteRequest);
        return ResultUtils.success(null);
    }

    /**
     * 根据ID查询菜单详情
     */
    @GetMapping("/get/{id}")
    @AuthCheck(mustRole = UserConstant.USER_ADMIN)
    public BaseResponse<SysMenuVO> getById(@PathVariable Long id) {
        return ResultUtils.success(menuService.getDetailById(id));
    }

    /**
     * 查询菜单树（如果前端需要树形结构）
     */
    @GetMapping("/tree")
    @AuthCheck(mustRole = UserConstant.USER_ADMIN)
    public BaseResponse<List<SysMenuTreeVO>> tree() {
        return ResultUtils.success(menuService.tree());
    }

    /**
     * 当前用户可见的菜单树：前端登录后拉这个接口来生成侧边栏与路由
     *
     * userId 只对管理员生效，普通用户传什么都会强制改回自己。
     */
    @GetMapping("/user_tree")
    public BaseResponse<List<SysMenuTreeVO>> getTreeByUser(@RequestParam(required = false) Long userId,
                                                          HttpServletRequest request) {
        UserVO currentUser = TokenUtil.getCurrentUser(request.getHeader("token"));
        ThrowUtils.throwException(currentUser == null || currentUser.getId() == null,
                ErrorCode.NOT_LOGIN_ERROR, "请先登录");
        Long targetUserId = UserConstant.USER_ADMIN.equals(currentUser.getUserRole())
                ? (userId != null ? userId : currentUser.getId())
                : currentUser.getId();
        return ResultUtils.success(menuService.getUserMenuTree(targetUserId));
    }
}
