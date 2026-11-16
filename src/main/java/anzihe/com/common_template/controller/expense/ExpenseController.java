package anzihe.com.common_template.controller.expense;

import anzihe.com.common_template.common.BaseResponse;
import anzihe.com.common_template.common.ResultUtils;
import anzihe.com.common_template.exception.ErrorCode;
import anzihe.com.common_template.model.DTO.expense.ExpenseBudgetDTO;
import anzihe.com.common_template.model.DTO.expense.ExpenseImportItemDTO;
import anzihe.com.common_template.model.DTO.expense.ExpenseRecordAddRequest;
import anzihe.com.common_template.model.DTO.expense.ExpenseRecordDeleteRequest;
import anzihe.com.common_template.model.DTO.expense.ExpenseRecordQueryRequest;
import anzihe.com.common_template.model.DTO.expense.ExpenseRecordUpdateRequest;
import anzihe.com.common_template.model.VO.expense.ExpenseCategoryStatVO;
import anzihe.com.common_template.model.VO.expense.ExpenseDailyStatVO;
import anzihe.com.common_template.model.VO.expense.ExpenseImportResultVO;
import anzihe.com.common_template.model.VO.expense.ExpenseMonthlyStatVO;
import anzihe.com.common_template.model.VO.expense.ExpenseRecordVO;
import anzihe.com.common_template.model.VO.expense.ExpenseSummaryVO;
import anzihe.com.common_template.model.VO.user.UserVO;
import anzihe.com.common_template.model.entity.ExpenseBudget;
import anzihe.com.common_template.service.ExpenseBudgetService;
import anzihe.com.common_template.service.ExpenseRecordService;
import anzihe.com.common_template.utils.ThrowUtils;
import anzihe.com.common_template.utils.TokenUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;

/**
 * 记账接口
 *
 * 所有接口的 userId 一律从 token 解析，不接受前端传参：
 * 否则「查谁的数据」就完全由调用方决定，改个 id 就能看别人的账本。
 */
@RestController
@RequestMapping("/expense")
public class ExpenseController {

    @Resource
    private ExpenseRecordService expenseRecordService;

    @Resource
    private ExpenseBudgetService expenseBudgetService;

    // ==================== 账单 ====================

    @PostMapping("/record/add")
    public BaseResponse<ExpenseRecordVO> addRecord(@RequestBody ExpenseRecordAddRequest request,
                                                   HttpServletRequest httpRequest) {
        return ResultUtils.success(expenseRecordService.addRecord(request, currentUserId(httpRequest)));
    }

    @PostMapping("/record/update")
    public BaseResponse<Boolean> updateRecord(@RequestBody ExpenseRecordUpdateRequest request,
                                              HttpServletRequest httpRequest) {
        return ResultUtils.success(expenseRecordService.updateRecord(request, currentUserId(httpRequest)));
    }

    @PostMapping("/record/delete")
    public BaseResponse<Boolean> deleteRecord(@RequestBody ExpenseRecordDeleteRequest request,
                                              HttpServletRequest httpRequest) {
        ThrowUtils.throwException(request == null, ErrorCode.PARAMS_ERROR, "参数为空");
        return ResultUtils.success(expenseRecordService.deleteRecord(request.getId(), currentUserId(httpRequest)));
    }

    @GetMapping("/record/get")
    public BaseResponse<ExpenseRecordVO> getRecord(@RequestParam("id") String id, HttpServletRequest httpRequest) {
        return ResultUtils.success(expenseRecordService.getRecord(id, currentUserId(httpRequest)));
    }

    @PostMapping("/record/page")
    public BaseResponse<Page<ExpenseRecordVO>> pageRecords(@RequestBody ExpenseRecordQueryRequest request,
                                                          HttpServletRequest httpRequest) {
        return ResultUtils.success(expenseRecordService.pageRecords(request, currentUserId(httpRequest)));
    }

    @PostMapping("/record/export")
    public BaseResponse<List<ExpenseRecordVO>> exportRecords(@RequestBody ExpenseRecordQueryRequest request,
                                                             HttpServletRequest httpRequest) {
        return ResultUtils.success(expenseRecordService.exportRecords(request, currentUserId(httpRequest)));
    }

    /**
     * 批量导入。前端解析 CSV 后按结构化数组提交，
     * 后端逐条校验：格式错的跳过并回报原因，已存在的算重复，不会整批回滚。
     */
    @PostMapping("/record/import")
    public BaseResponse<ExpenseImportResultVO> importRecords(@RequestBody List<ExpenseImportItemDTO> records,
                                                             HttpServletRequest httpRequest) {
        return ResultUtils.success(expenseRecordService.importRecords(records, currentUserId(httpRequest)));
    }

    /**
     * 当前账号的账单条数。
     * 「系统设置」页用它判断数据是否已清空：初始化导入前必须先确认这一项为 0。
     */
    @GetMapping("/record/count")
    public BaseResponse<Long> countRecords(HttpServletRequest httpRequest) {
        return ResultUtils.success(expenseRecordService.countRecords(currentUserId(httpRequest)));
    }

    /**
     * 清空当前账号的全部账单（逻辑删除），返回被清掉的条数。
     *
     * 只清账单不清预算：清空是为了重新初始化数据，预算属于配置，没必要逼用户再设一次。
     */
    @PostMapping("/record/clear")
    public BaseResponse<Integer> clearRecords(HttpServletRequest httpRequest) {
        return ResultUtils.success(expenseRecordService.clearAll(currentUserId(httpRequest)));
    }

    // ==================== 统计 ====================

    @GetMapping("/summary")
    public BaseResponse<ExpenseSummaryVO> summary(HttpServletRequest httpRequest) {
        return ResultUtils.success(expenseRecordService.getSummary(currentUserId(httpRequest)));
    }

    /**
     * 分类汇总
     *
     * @param type  daily / site，不传表示全部
     * @param start 起始日期，含当天
     * @param end   结束日期，含当天
     */
    @GetMapping("/stats/category")
    public BaseResponse<List<ExpenseCategoryStatVO>> categoryStat(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "start", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(value = "end", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            HttpServletRequest httpRequest) {
        return ResultUtils.success(
                expenseRecordService.getCategoryStat(type, start, end, currentUserId(httpRequest)));
    }

    /**
     * 月度趋势，默认近 12 个月（含当月），无数据的月份补 0 保证横轴连续
     */
    @GetMapping("/stats/monthly")
    public BaseResponse<List<ExpenseMonthlyStatVO>> monthlyStat(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "months", defaultValue = "12") int months,
            HttpServletRequest httpRequest) {
        return ResultUtils.success(
                expenseRecordService.getMonthlyStat(type, months, currentUserId(httpRequest)));
    }

    /**
     * 按天汇总，看板日历用。
     *
     * @param month yyyy-MM，不传取当月；只返回有消费的日期，前端按日期匹配格子
     */
    @GetMapping("/stats/daily")
    public BaseResponse<List<ExpenseDailyStatVO>> dailyStat(
            @RequestParam(value = "month", required = false) String month,
            HttpServletRequest httpRequest) {
        return ResultUtils.success(
                expenseRecordService.getDailyStat(month, currentUserId(httpRequest)));
    }

    // ==================== 预算 ====================

    @GetMapping("/budget")
    public BaseResponse<ExpenseBudgetDTO> getBudget(HttpServletRequest httpRequest) {
        ExpenseBudget budget = expenseBudgetService.getByUserId(currentUserId(httpRequest));
        ExpenseBudgetDTO dto = new ExpenseBudgetDTO();
        dto.setMonthlyTotalCents(budget == null || budget.getMonthlyTotalCents() == null
                ? 0L : budget.getMonthlyTotalCents());
        dto.setMonthlySiteCents(budget == null || budget.getMonthlySiteCents() == null
                ? 0L : budget.getMonthlySiteCents());
        return ResultUtils.success(dto);
    }

    @PostMapping("/budget/save")
    public BaseResponse<Boolean> saveBudget(@RequestBody ExpenseBudgetDTO request, HttpServletRequest httpRequest) {
        expenseBudgetService.saveBudget(request, currentUserId(httpRequest));
        return ResultUtils.success(true);
    }

    // ==================== 内部 ====================

    /**
     * 从请求头 token 解析当前用户 id。
     *
     * 这里不用 userService.currentUser()：那个方法每次都会回表查用户并刷新 token，
     * 记账接口调用频繁，没必要为拿一个 id 多打一次库。
     * token 无效一律抛 NOT_LOGIN_ERROR(40100)，前端拦截器据此跳登录页。
     */
    private Long currentUserId(HttpServletRequest request) {
        String token = request.getHeader("token");
        ThrowUtils.throwException(!TokenUtil.verify(token), ErrorCode.NOT_LOGIN_ERROR);
        UserVO user = TokenUtil.getCurrentUser(token);
        ThrowUtils.throwException(user == null || user.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        return user.getId();
    }
}
