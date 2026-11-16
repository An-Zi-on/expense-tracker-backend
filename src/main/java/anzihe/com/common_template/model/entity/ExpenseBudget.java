package anzihe.com.common_template.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 预算设置，每个用户一行
 */
@TableName("expense_budget")
@Data
public class ExpenseBudget {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 每月总预算（分），0 表示不设置 */
    private Long monthlyTotalCents;

    /** 每月网站开发预算（分），0 表示不设置 */
    private Long monthlySiteCents;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
