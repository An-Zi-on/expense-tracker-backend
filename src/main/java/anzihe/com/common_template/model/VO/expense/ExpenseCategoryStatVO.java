package anzihe.com.common_template.model.VO.expense;

import lombok.Data;

@Data
public class ExpenseCategoryStatVO {

    /** 分类名，如「餐饮」「服务器」 */
    private String category;

    /** 该类合计（分） */
    private Long amountCents;

    /** 该类记录条数 */
    private Long total;

    /** 占比（0~1），由 Service 填充 */
    private Double percent;
}
