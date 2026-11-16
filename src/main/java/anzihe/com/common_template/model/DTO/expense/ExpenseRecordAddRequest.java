package anzihe.com.common_template.model.DTO.expense;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ExpenseRecordAddRequest {

    /**
     * 可选。不传则由后端生成 UUID。
     * 传了就作为幂等键 —— 离线补传、脚本重试重复提交同一条不会产生两条记录。
     */
    private String id;

    private LocalDate recordDate;

    /** 金额（分），必须为正整数 */
    private Long amountCents;

    /** daily / site */
    private String expenseType;

    private String category;

    private String project;

    private String payment;

    private String note;

    /** manual / import / receipt，默认 manual */
    private String source;
}
