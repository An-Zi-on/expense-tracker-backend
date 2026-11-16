package anzihe.com.common_template.model.DTO.expense;

import anzihe.com.common_template.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@EqualsAndHashCode(callSuper = true)
@Data
public class ExpenseRecordQueryRequest extends PageRequest {

    private LocalDate startDate;

    private LocalDate endDate;

    /** daily / site，空表示不限 */
    private String expenseType;

    private String category;

    /** 项目名，模糊匹配 */
    private String project;

    /** 关键词，同时匹配备注与项目名 */
    private String keyword;

    private Long minCents;

    private Long maxCents;
}
