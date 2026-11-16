package anzihe.com.common_template.model.VO.expense;

import lombok.Data;

/**
 * 按天汇总，看板日历用。
 *
 * day 用 String 而不是 LocalDate，和 ExpenseMonthlyStatVO.month 保持一致：
 * 前端直接拿它当 Map 的 key 去匹配日历格子的日期，省掉一次时区转换。
 */
@Data
public class ExpenseDailyStatVO {

    /** 日期，形如 2026-11-14 */
    private String day;

    /** 当天合计（分） */
    private Long amountCents;

    /** 当天记录条数 */
    private Long total;
}
