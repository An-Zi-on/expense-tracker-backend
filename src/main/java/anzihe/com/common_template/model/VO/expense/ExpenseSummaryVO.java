package anzihe.com.common_template.model.VO.expense;

import lombok.Data;

/**
 * 看板总览。前 8 个字段由一条聚合 SQL 直接查出，后 3 个由 Service 补齐。
 */
@Data
public class ExpenseSummaryVO {

    /** 总支出（分） */
    private Long totalCents;

    /** 今日支出（分） */
    private Long todayCents;

    /** 本月支出（分） */
    private Long monthCents;

    /** 本年支出（分） */
    private Long yearCents;

    /** 网站开发累计支出（分） */
    private Long siteTotalCents;

    /** 网站开发本月支出（分） */
    private Long siteMonthCents;

    /** 记录总条数 */
    private Long recordCount;

    /** 网站开发记录条数 */
    private Long siteRecordCount;

    /** 当月，形如 2026-11 */
    private String month;

    /** 每月总预算（分），0 表示未设置 */
    private Long budgetTotalCents;

    /** 每月网站开发预算（分），0 表示未设置 */
    private Long budgetSiteCents;

    /** 日常开销本月支出（分）= monthCents - siteMonthCents，方便前端直接画两条进度条 */
    private Long dailyMonthCents;
}
