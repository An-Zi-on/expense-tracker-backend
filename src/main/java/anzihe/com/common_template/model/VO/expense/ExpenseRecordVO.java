package anzihe.com.common_template.model.VO.expense;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ExpenseRecordVO {

    private String id;

    private LocalDate recordDate;

    /** 金额（分）。前端展示时再除以 100 */
    private Long amountCents;

    /** daily=日常开销 site=网站开发 */
    private String expenseType;

    private String category;

    private String project;

    private String payment;

    private String note;

    private String source;

    private LocalDateTime createTime;
}
