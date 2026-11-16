package anzihe.com.common_template.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 记账流水
 *
 * 注意 amountCents：金额一律存「分」，类型是 Long 而不是 double。
 * 前端负责 元 -> 分 的换算，后端全程整数运算，从根上避免 0.1 + 0.2 这类浮点误差
 * 在统计汇总时被放大。
 */
@TableName("expense_record")
@Data
public class ExpenseRecord {

    /** 客户端生成的 UUID，同时充当幂等键 */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 数据归属，只能由后端从 token 解析后写入，绝不接收前端传值 */
    private Long userId;

    private LocalDate recordDate;

    /** 金额（分） */
    private Long amountCents;

    /** daily=日常开销 site=网站开发 */
    private String expenseType;

    private String category;

    private String project;

    private String payment;

    private String note;

    /** manual / import / receipt */
    private String source;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDelete;
}
