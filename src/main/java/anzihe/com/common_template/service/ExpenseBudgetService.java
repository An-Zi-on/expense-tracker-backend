package anzihe.com.common_template.service;

import anzihe.com.common_template.model.DTO.expense.ExpenseBudgetDTO;
import anzihe.com.common_template.model.entity.ExpenseBudget;

public interface ExpenseBudgetService {

    /** 查不到返回 null，表示用户从未设置过预算 */
    ExpenseBudget getByUserId(Long userId);

    void saveBudget(ExpenseBudgetDTO request, Long userId);
}
