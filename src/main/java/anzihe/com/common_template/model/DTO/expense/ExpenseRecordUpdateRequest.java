package anzihe.com.common_template.model.DTO.expense;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ExpenseRecordUpdateRequest {

    private String id;

    private LocalDate recordDate;

    private Long amountCents;

    private String expenseType;

    private String category;

    private String project;

    private String payment;

    private String note;
}
