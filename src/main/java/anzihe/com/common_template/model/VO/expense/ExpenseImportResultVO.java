package anzihe.com.common_template.model.VO.expense;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ExpenseImportResultVO {

    /** 成功写入条数 */
    private int imported;

    /** 因 id 已存在而跳过的条数（幂等重放） */
    private int duplicated;

    /** 校验失败条数 */
    private int failed;

    /** 失败原因，最多保留前 20 条，避免响应体过大 */
    private List<String> errors = new ArrayList<>();
}
