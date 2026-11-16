package anzihe.com.common_template.model.DTO.expense;

import lombok.Data;

/**
 * 导入专用条目。
 *
 * 与 ExpenseRecordAddRequest 的唯一区别是 recordDate 用 String 接收：
 * 导入的数据来自用户手里的 CSV，日期格式千奇百怪，甚至可能有 2026-13-40 这种脏值。
 * 若直接声明成 LocalDate，Jackson 在反序列化阶段就会抛异常，
 * 整批 500 条全部失败 —— 一条脏数据拖垮整次导入是不可接受的。
 * 所以这里收成 String，交给 Service 逐条解析，坏的那条单独报错。
 */
@Data
public class ExpenseImportItemDTO {

    private String id;

    private String recordDate;

    private Long amountCents;

    private String expenseType;

    private String category;

    private String project;

    private String payment;

    private String note;

    private String source;
}
