package anzihe.com.common_template.model.VO.expense;

import lombok.Data;

@Data
public class ExpenseMonthlyStatVO {

    /** 月份，形如 2026-11 */
    private String month;

    /** 该月合计（分） */
    private Long amountCents;

    /** 该月记录条数 */
    private Long total;
}
