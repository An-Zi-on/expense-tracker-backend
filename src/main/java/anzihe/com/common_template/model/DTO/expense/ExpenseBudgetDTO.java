package anzihe.com.common_template.model.DTO.expense;

import lombok.Data;

@Data
public class ExpenseBudgetDTO {

    /** 每月总预算（分），0 或 null 表示取消预算 */
    private Long monthlyTotalCents;

    /** 每月网站开发预算（分） */
    private Long monthlySiteCents;
}
