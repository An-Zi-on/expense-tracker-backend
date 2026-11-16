package anzihe.com.common_template.controller.system;

import anzihe.com.common_template.annotation.AuthCheck;
import anzihe.com.common_template.common.BaseResponse;
import anzihe.com.common_template.common.DeleteRequest;
import anzihe.com.common_template.common.ResultUtils;
import anzihe.com.common_template.common.UserConstant;
import anzihe.com.common_template.model.DTO.usermenu.SysUserMenuDTO;
import anzihe.com.common_template.model.VO.usermenu.SysUserMenuVO;
import anzihe.com.common_template.model.entity.SysUserMenu;
import anzihe.com.common_template.service.SysUserMenuService;
import cn.hutool.core.bean.BeanUtil;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

/**
 * 用户级菜单授权（角色的补充，给个别用户额外开菜单）—— 仅管理员可用
 */
@RestController
@RequestMapping("/user_menu")
public class UserMenuController {

    @Resource
    private SysUserMenuService userMenuService;

    @PostMapping("/save")
    @AuthCheck(mustRole = UserConstant.USER_ADMIN)
    public BaseResponse<Boolean> save (@RequestBody SysUserMenuDTO userMenuDTO){
        boolean save = userMenuService.save(BeanUtil.toBean(userMenuDTO, SysUserMenu.class));
        return ResultUtils.success(save);
    }

    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.USER_ADMIN)
    public BaseResponse<Boolean> delete (@RequestBody DeleteRequest deleteRequest){
        Long id = deleteRequest.getId();
        boolean remove = userMenuService.removeById(id);
        return ResultUtils.success(remove);
    }

    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.USER_ADMIN)
    public BaseResponse<Boolean> update(@RequestBody SysUserMenuDTO userMenuDTO){
        boolean update = userMenuService.updateById(BeanUtil.toBean(userMenuDTO,SysUserMenu.class));
        return ResultUtils.success(update);
    }

    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.USER_ADMIN)
    public  BaseResponse<SysUserMenuVO> deteli (@RequestParam Long id){
        SysUserMenuVO userMenuVO = BeanUtil.toBean(userMenuService.getById(id), SysUserMenuVO.class);
        return ResultUtils.success(userMenuVO);
    }

}
