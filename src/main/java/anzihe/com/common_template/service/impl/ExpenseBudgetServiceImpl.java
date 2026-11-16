package anzihe.com.common_template.service.impl;

import anzihe.com.common_template.exception.ErrorCode;
import anzihe.com.common_template.mapper.ExpenseBudgetMapper;
import anzihe.com.common_template.model.DTO.expense.ExpenseBudgetDTO;
import anzihe.com.common_template.model.entity.ExpenseBudget;
import anzihe.com.common_template.service.ExpenseBudgetService;
import anzihe.com.common_template.utils.ThrowUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

@Service
public class ExpenseBudgetServiceImpl implements ExpenseBudgetService {

    /** 单月预算上限 1000 万元，纯粹是防手抖 */
    private static final long MAX_BUDGET_CENTS = 1_000_000_000L;

    @Resource
    private ExpenseBudgetMapper expenseBudgetMapper;

    @Override
    public ExpenseBudget getByUserId(Long userId) {
        QueryWrapper<ExpenseBudget> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        return expenseBudgetMapper.selectOne(wrapper);
    }

    @Override
    public void saveBudget(ExpenseBudgetDTO request, Long userId) {
        ThrowUtils.throwException(request == null, ErrorCode.PARAMS_ERROR, "参数为空");

        long total = request.getMonthlyTotalCents() == null ? 0L : request.getMonthlyTotalCents();
        long site = request.getMonthlySiteCents() == null ? 0L : request.getMonthlySiteCents();

        ThrowUtils.throwException(total < 0 || site < 0, ErrorCode.PARAMS_ERROR, "预算不能为负数");
        ThrowUtils.throwException(total > MAX_BUDGET_CENTS || site > MAX_BUDGET_CENTS,
                ErrorCode.PARAMS_ERROR, "预算金额过大");
        // 总预算为 0 表示「不限制」，此时只设网站预算也算合法
        ThrowUtils.throwException(total > 0 && site > total,
                ErrorCode.PARAMS_ERROR, "网站开发预算不能超过总预算");

        ExpenseBudget exist = getByUserId(userId);
        if (exist == null) {
            ExpenseBudget budget = new ExpenseBudget();
            budget.setUserId(userId);
            budget.setMonthlyTotalCents(total);
            budget.setMonthlySiteCents(site);
            expenseBudgetMapper.insert(budget);
            return;
        }

        // 用 UpdateWrapper 显式 set：updateById 会忽略 null，
        // 而这里的 0 是「取消预算」的有效值，不能被当成「不更新」
        LambdaUpdateWrapper<ExpenseBudget> wrapper = new LambdaUpdateWrapper<ExpenseBudget>()
                .eq(ExpenseBudget::getUserId, userId)
                .set(ExpenseBudget::getMonthlyTotalCents, total)
                .set(ExpenseBudget::getMonthlySiteCents, site);
        expenseBudgetMapper.update(null, wrapper);
    }
}
