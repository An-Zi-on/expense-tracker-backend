package anzihe.com.common_template.service;

import anzihe.com.common_template.model.DTO.expense.ExpenseImportItemDTO;
import anzihe.com.common_template.model.DTO.expense.ExpenseRecordAddRequest;
import anzihe.com.common_template.model.DTO.expense.ExpenseRecordQueryRequest;
import anzihe.com.common_template.model.DTO.expense.ExpenseRecordUpdateRequest;
import anzihe.com.common_template.model.VO.expense.ExpenseCategoryStatVO;
import anzihe.com.common_template.model.VO.expense.ExpenseDailyStatVO;
import anzihe.com.common_template.model.VO.expense.ExpenseImportResultVO;
import anzihe.com.common_template.model.VO.expense.ExpenseMonthlyStatVO;
import anzihe.com.common_template.model.VO.expense.ExpenseRecordVO;
import anzihe.com.common_template.model.VO.expense.ExpenseSummaryVO;
import anzihe.com.common_template.model.entity.ExpenseRecord;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;

import java.time.LocalDate;
import java.util.List;

/**
 * 记账流水服务。所有方法的 userId 都由 Controller 从 token 解析后传入，
 * 不接收前端自报的 userId —— 否则改个参数就能读别人的账本。
 */
public interface ExpenseRecordService extends IService<ExpenseRecord> {

    ExpenseRecordVO addRecord(ExpenseRecordAddRequest request, Long userId);

    boolean updateRecord(ExpenseRecordUpdateRequest request, Long userId);

    boolean deleteRecord(String id, Long userId);

    ExpenseRecordVO getRecord(String id, Long userId);

    Page<ExpenseRecordVO> pageRecords(ExpenseRecordQueryRequest request, Long userId);

    /** 导出用：不分页，但限制上限，避免一次拖走整张表 */
    List<ExpenseRecordVO> exportRecords(ExpenseRecordQueryRequest request, Long userId);

    ExpenseSummaryVO getSummary(Long userId);

    List<ExpenseCategoryStatVO> getCategoryStat(String type, LocalDate start, LocalDate end, Long userId);

    List<ExpenseMonthlyStatVO> getMonthlyStat(String type, int months, Long userId);

    /**
     * 某个月的按天汇总，供看板日历使用。month 为空取当月，格式 yyyy-MM。
     */
    List<ExpenseDailyStatVO> getDailyStat(String month, Long userId);

    ExpenseImportResultVO importRecords(List<ExpenseImportItemDTO> records, Long userId);

    /** 当前账号的有效账单条数 */
    long countRecords(Long userId);

    /** 清空当前账号的全部账单，返回被清掉的条数 */
    int clearAll(Long userId);
}
