package anzihe.com.common_template.service.impl;

import anzihe.com.common_template.common.ExpenseConstants;
import anzihe.com.common_template.exception.ErrorCode;
import anzihe.com.common_template.mapper.ExpenseBudgetMapper;
import anzihe.com.common_template.mapper.ExpenseRecordMapper;
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
import anzihe.com.common_template.model.entity.ExpenseBudget;
import anzihe.com.common_template.model.entity.ExpenseRecord;
import anzihe.com.common_template.service.ExpenseRecordService;
import anzihe.com.common_template.utils.ThrowUtils;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ExpenseRecordServiceImpl extends ServiceImpl<ExpenseRecordMapper, ExpenseRecord>
        implements ExpenseRecordService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    /** 专门用来解析入参：yyyy 是纪元年，缺 era 会解析失败，所以用 uuuu */
    private static final DateTimeFormatter YEAR_MONTH_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM");

    private static final int MAX_PAGE_SIZE = 200;

    /** 单次导出上限，超过这个量的数据更适合直接查库 */
    private static final int MAX_EXPORT_SIZE = 10000;

    @Resource
    private ExpenseBudgetMapper expenseBudgetMapper;

    @Override
    public ExpenseRecordVO addRecord(ExpenseRecordAddRequest request, Long userId) {
        ExpenseRecord record = buildRecord(request, userId);
        String id = StrUtil.isBlank(request.getId()) ? IdUtil.simpleUUID() : request.getId().trim();
        record.setId(id);

        ExpenseRecord exist = baseMapper.selectByIdIgnoreLogic(id);
        if (exist != null) {
            ThrowUtils.throwException(!userId.equals(exist.getUserId()), ErrorCode.NO_AUTH_ERROR, "该记录不属于当前用户");
            if (Integer.valueOf(0).equals(exist.getIsDelete())) {
                // 幂等：同一个 id 重复提交，直接返回已有记录，不报错也不产生第二条
                return toVO(exist);
            }
            // 曾经被删过，复活并覆盖成这次提交的内容
            baseMapper.revive(record);
            return toVO(baseMapper.selectByIdIgnoreLogic(id));
        }

        ThrowUtils.throwException(!this.save(record), ErrorCode.OPERATION_ERROR, "保存失败");
        return toVO(record);
    }

    @Override
    public boolean updateRecord(ExpenseRecordUpdateRequest request, Long userId) {
        ThrowUtils.throwException(request == null || StrUtil.isBlank(request.getId()),
                ErrorCode.PARAMS_ERROR, "记录 ID 不能为空");
        ExpenseRecord exist = this.getById(request.getId().trim());
        ThrowUtils.throwException(exist == null, ErrorCode.NOT_FOUND_ERROR, "记录不存在");
        ThrowUtils.throwException(!userId.equals(exist.getUserId()), ErrorCode.NO_AUTH_ERROR);

        ExpenseRecord update = new ExpenseRecord();
        update.setId(exist.getId());

        String type = exist.getExpenseType();
        if (StrUtil.isNotBlank(request.getExpenseType())) {
            type = request.getExpenseType().trim();
            ThrowUtils.throwException(!ExpenseConstants.isValidType(type), ErrorCode.PARAMS_ERROR, "支出类型不合法");
            update.setExpenseType(type);
        }
        if (request.getCategory() != null) {
            // 分类必须跟着最终类型走，否则会出现「日常开销 + 服务器」这种组合
            update.setCategory(ExpenseConstants.normalizeCategory(type, request.getCategory()));
        }
        if (request.getRecordDate() != null) {
            checkDate(request.getRecordDate());
            update.setRecordDate(request.getRecordDate());
        }
        if (request.getAmountCents() != null) {
            checkAmount(request.getAmountCents());
            update.setAmountCents(request.getAmountCents());
        }
        if (request.getProject() != null) {
            update.setProject(trimToNull(request.getProject(), 64));
        }
        if (request.getPayment() != null) {
            update.setPayment(trimToNull(request.getPayment(), 16));
        }
        if (request.getNote() != null) {
            update.setNote(trimToNull(request.getNote(), 255));
        }
        return this.updateById(update);
    }

    @Override
    public boolean deleteRecord(String id, Long userId) {
        ThrowUtils.throwException(StrUtil.isBlank(id), ErrorCode.PARAMS_ERROR, "记录 ID 不能为空");
        ExpenseRecord exist = this.getById(id.trim());
        ThrowUtils.throwException(exist == null, ErrorCode.NOT_FOUND_ERROR, "记录不存在");
        ThrowUtils.throwException(!userId.equals(exist.getUserId()), ErrorCode.NO_AUTH_ERROR);
        return this.removeById(exist.getId());
    }

    @Override
    public ExpenseRecordVO getRecord(String id, Long userId) {
        ThrowUtils.throwException(StrUtil.isBlank(id), ErrorCode.PARAMS_ERROR, "记录 ID 不能为空");
        ExpenseRecord record = this.getById(id.trim());
        ThrowUtils.throwException(record == null, ErrorCode.NOT_FOUND_ERROR, "记录不存在");
        ThrowUtils.throwException(!userId.equals(record.getUserId()), ErrorCode.NO_AUTH_ERROR);
        return toVO(record);
    }

    @Override
    public Page<ExpenseRecordVO> pageRecords(ExpenseRecordQueryRequest request, Long userId) {
        ThrowUtils.throwException(request == null, ErrorCode.PARAMS_ERROR, "查询条件为空");
        int current = Math.max(request.getCurrent(), 1);
        int pageSize = Math.min(Math.max(request.getPageSize(), 1), MAX_PAGE_SIZE);

        Page<ExpenseRecord> page = this.page(new Page<>(current, pageSize), buildWrapper(request, userId));
        Page<ExpenseRecordVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return result;
    }

    @Override
    public List<ExpenseRecordVO> exportRecords(ExpenseRecordQueryRequest request, Long userId) {
        ThrowUtils.throwException(request == null, ErrorCode.PARAMS_ERROR, "查询条件为空");
        QueryWrapper<ExpenseRecord> wrapper = buildWrapper(request, userId);
        wrapper.last("LIMIT " + MAX_EXPORT_SIZE);
        return this.list(wrapper).stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 列表与导出共用的查询条件。
     * 注意列名必须写数据库字段（record_date），QueryWrapper 不做驼峰转换。
     */
    private QueryWrapper<ExpenseRecord> buildWrapper(ExpenseRecordQueryRequest request, Long userId) {
        QueryWrapper<ExpenseRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        wrapper.ge(request.getStartDate() != null, "record_date", request.getStartDate());
        wrapper.le(request.getEndDate() != null, "record_date", request.getEndDate());
        wrapper.eq(StrUtil.isNotBlank(request.getExpenseType()), "expense_type", request.getExpenseType());
        wrapper.eq(StrUtil.isNotBlank(request.getCategory()), "category", request.getCategory());
        wrapper.like(StrUtil.isNotBlank(request.getProject()), "project", request.getProject());
        wrapper.ge(request.getMinCents() != null, "amount_cents", request.getMinCents());
        wrapper.le(request.getMaxCents() != null, "amount_cents", request.getMaxCents());
        if (StrUtil.isNotBlank(request.getKeyword())) {
            String keyword = request.getKeyword().trim();
            wrapper.and(q -> q.like("note", keyword)
                    .or().like("project", keyword)
                    .or().like("category", keyword));
        }
        wrapper.orderByDesc("record_date").orderByDesc("create_time");
        return wrapper;
    }

    @Override
    public ExpenseSummaryVO getSummary(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());
        LocalDate yearStart = today.withDayOfYear(1);
        LocalDate yearEnd = today.withDayOfYear(today.lengthOfYear());

        ExpenseSummaryVO summary = baseMapper.selectSummary(userId, today, monthStart, monthEnd, yearStart, yearEnd);
        if (summary == null) {
            summary = new ExpenseSummaryVO();
        }
        summary.setTotalCents(zeroIfNull(summary.getTotalCents()));
        summary.setTodayCents(zeroIfNull(summary.getTodayCents()));
        summary.setMonthCents(zeroIfNull(summary.getMonthCents()));
        summary.setYearCents(zeroIfNull(summary.getYearCents()));
        summary.setSiteTotalCents(zeroIfNull(summary.getSiteTotalCents()));
        summary.setSiteMonthCents(zeroIfNull(summary.getSiteMonthCents()));
        summary.setRecordCount(zeroIfNull(summary.getRecordCount()));
        summary.setSiteRecordCount(zeroIfNull(summary.getSiteRecordCount()));
        summary.setDailyMonthCents(Math.max(0, summary.getMonthCents() - summary.getSiteMonthCents()));
        summary.setMonth(today.format(MONTH_FORMAT));

        ExpenseBudget budget = findBudget(userId);
        summary.setBudgetTotalCents(budget == null ? 0L : zeroIfNull(budget.getMonthlyTotalCents()));
        summary.setBudgetSiteCents(budget == null ? 0L : zeroIfNull(budget.getMonthlySiteCents()));
        return summary;
    }

    @Override
    public List<ExpenseCategoryStatVO> getCategoryStat(String type, LocalDate start, LocalDate end, Long userId) {
        String normalizedType = StrUtil.isBlank(type) ? null : type.trim();
        ThrowUtils.throwException(normalizedType != null && !ExpenseConstants.isValidType(normalizedType),
                ErrorCode.PARAMS_ERROR, "支出类型不合法");

        List<ExpenseCategoryStatVO> list = baseMapper.sumByCategory(userId, normalizedType, start, end);
        if (list == null || list.isEmpty()) {
            return new ArrayList<>();
        }
        long total = list.stream()
                .mapToLong(item -> zeroIfNull(item.getAmountCents()))
                .sum();
        list.forEach(item -> {
            item.setAmountCents(zeroIfNull(item.getAmountCents()));
            item.setTotal(zeroIfNull(item.getTotal()));
            item.setPercent(total > 0 ? (double) item.getAmountCents() / total : 0D);
        });
        return list;
    }

    @Override
    public List<ExpenseMonthlyStatVO> getMonthlyStat(String type, int months, Long userId) {
        String normalizedType = StrUtil.isBlank(type) ? null : type.trim();
        ThrowUtils.throwException(normalizedType != null && !ExpenseConstants.isValidType(normalizedType),
                ErrorCode.PARAMS_ERROR, "支出类型不合法");

        int span = Math.min(Math.max(months, 1), 36);
        YearMonth endMonth = YearMonth.now();
        YearMonth startMonth = endMonth.minusMonths(span - 1L);

        List<ExpenseMonthlyStatVO> rows = baseMapper.sumByMonth(
                userId, normalizedType, startMonth.atDay(1), endMonth.atEndOfMonth());

        Map<String, ExpenseMonthlyStatVO> indexed = new HashMap<>();
        if (rows != null) {
            for (ExpenseMonthlyStatVO row : rows) {
                indexed.put(row.getMonth(), row);
            }
        }

        // 补全没有数据的月份：趋势图需要连续的横轴，缺月会让柱子挤在一起
        List<ExpenseMonthlyStatVO> result = new ArrayList<>(span);
        for (int i = 0; i < span; i++) {
            String month = startMonth.plusMonths(i).format(MONTH_FORMAT);
            ExpenseMonthlyStatVO row = indexed.get(month);
            if (row == null) {
                row = new ExpenseMonthlyStatVO();
                row.setMonth(month);
                row.setAmountCents(0L);
                row.setTotal(0L);
            } else {
                row.setAmountCents(zeroIfNull(row.getAmountCents()));
                row.setTotal(zeroIfNull(row.getTotal()));
            }
            result.add(row);
        }
        return result;
    }

    @Override
    public List<ExpenseDailyStatVO> getDailyStat(String month, Long userId) {
        YearMonth target = YearMonth.now();
        if (StrUtil.isNotBlank(month)) {
            try {
                // 用 uuuu 而不是 yyyy 解析：yyyy 是「纪元年」，
                // YearMonth.parse 在没有 era 的情况下会抛异常
                target = YearMonth.parse(month.trim(), YEAR_MONTH_FORMAT);
            } catch (DateTimeParseException e) {
                ThrowUtils.throwException(true, ErrorCode.PARAMS_ERROR, "月份格式应为 yyyy-MM");
            }
        }

        List<ExpenseDailyStatVO> rows = baseMapper.sumByDay(
                userId, target.atDay(1), target.atEndOfMonth());
        if (rows == null) {
            return new ArrayList<>();
        }
        rows.forEach(row -> {
            row.setAmountCents(zeroIfNull(row.getAmountCents()));
            row.setTotal(zeroIfNull(row.getTotal()));
        });
        return rows;
    }

    @Override
    public ExpenseImportResultVO importRecords(List<ExpenseImportItemDTO> records, Long userId) {
        ThrowUtils.throwException(records == null || records.isEmpty(), ErrorCode.PARAMS_ERROR, "导入数据为空");
        ThrowUtils.throwException(records.size() > ExpenseConstants.MAX_IMPORT_SIZE, ErrorCode.PARAMS_ERROR,
                "单次最多导入 " + ExpenseConstants.MAX_IMPORT_SIZE + " 条");

        ExpenseImportResultVO result = new ExpenseImportResultVO();
        for (int i = 0; i < records.size(); i++) {
            ExpenseImportItemDTO item = records.get(i);
            try {
                // 逐条独立处理：某一条格式不对不该让整批回滚
                ThrowUtils.throwException(item == null, ErrorCode.PARAMS_ERROR, "数据为空");

                LocalDate recordDate = parseImportDate(item.getRecordDate());
                ThrowUtils.throwException(recordDate == null, ErrorCode.PARAMS_ERROR,
                        "日期「" + StrUtil.nullToEmpty(item.getRecordDate()) + "」无法识别");

                ExpenseRecordAddRequest request = new ExpenseRecordAddRequest();
                request.setId(item.getId());
                request.setRecordDate(recordDate);
                request.setAmountCents(item.getAmountCents());
                request.setExpenseType(item.getExpenseType());
                request.setCategory(item.getCategory());
                request.setProject(item.getProject());
                request.setPayment(item.getPayment());
                request.setNote(item.getNote());
                request.setSource(item.getSource());

                String id = StrUtil.isBlank(request.getId())
                        ? IdUtil.simpleUUID()
                        : request.getId().trim();
                ExpenseRecord exist = baseMapper.selectByIdIgnoreLogic(id);
                if (exist != null) {
                    if (!userId.equals(exist.getUserId())) {
                        throw new IllegalStateException("该记录 ID 已被其他账号占用");
                    }
                    if (Integer.valueOf(0).equals(exist.getIsDelete())) {
                        result.setDuplicated(result.getDuplicated() + 1);
                        continue;
                    }
                }
                ExpenseRecord record = buildRecord(request, userId);
                record.setId(id);
                if (exist != null) {
                    baseMapper.revive(record);
                } else {
                    this.save(record);
                }
                result.setImported(result.getImported() + 1);
            } catch (Exception e) {
                result.setFailed(result.getFailed() + 1);
                if (result.getErrors().size() < 20) {
                    result.getErrors().add("第 " + (i + 1) + " 条：" + e.getMessage());
                }
            }
        }
        return result;
    }

    @Override
    public long countRecords(Long userId) {
        ThrowUtils.throwException(userId == null, ErrorCode.NOT_LOGIN_ERROR);
        return baseMapper.countByUser(userId);
    }

    /**
     * 清空全部账单。
     *
     * 注意这里不删预算：清空是针对「账单数据」的，预算属于配置，
     * 用户清空重来一遍账本，没道理让他把预算再设一次。
     */
    @Override
    public int clearAll(Long userId) {
        ThrowUtils.throwException(userId == null, ErrorCode.NOT_LOGIN_ERROR);
        return baseMapper.clearByUser(userId);
    }

    // ==================== 内部工具 ====================

    private ExpenseBudget findBudget(Long userId) {
        QueryWrapper<ExpenseBudget> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        return expenseBudgetMapper.selectOne(wrapper);
    }

    private ExpenseRecord buildRecord(ExpenseRecordAddRequest request, Long userId) {
        ThrowUtils.throwException(request == null, ErrorCode.PARAMS_ERROR, "参数为空");
        checkAmount(request.getAmountCents());
        checkDate(request.getRecordDate());

        String type = StrUtil.isBlank(request.getExpenseType())
                ? ExpenseConstants.TYPE_DAILY
                : request.getExpenseType().trim();
        ThrowUtils.throwException(!ExpenseConstants.isValidType(type), ErrorCode.PARAMS_ERROR,
                "支出类型只能是 daily 或 site");

        ExpenseRecord record = new ExpenseRecord();
        record.setUserId(userId);
        record.setRecordDate(request.getRecordDate());
        record.setAmountCents(request.getAmountCents());
        record.setExpenseType(type);
        record.setCategory(ExpenseConstants.normalizeCategory(type, request.getCategory()));
        record.setProject(trimToNull(request.getProject(), 64));
        record.setPayment(trimToNull(request.getPayment(), 16));
        record.setNote(trimToNull(request.getNote(), 255));
        record.setSource(StrUtil.isBlank(request.getSource())
                ? ExpenseConstants.SOURCE_MANUAL
                : request.getSource().trim());
        record.setIsDelete(0);
        return record;
    }

    private void checkAmount(Long amountCents) {
        ThrowUtils.throwException(amountCents == null || amountCents <= 0,
                ErrorCode.PARAMS_ERROR, "金额必须大于 0");
        ThrowUtils.throwException(amountCents > ExpenseConstants.MAX_AMOUNT_CENTS,
                ErrorCode.PARAMS_ERROR, "单笔金额不能超过 100 万元");
    }

    private void checkDate(LocalDate date) {
        ThrowUtils.throwException(date == null, ErrorCode.PARAMS_ERROR, "日期不能为空");
        ThrowUtils.throwException(date.getYear() < 2000 || date.getYear() > 2100,
                ErrorCode.PARAMS_ERROR, "日期不在合理范围内");
    }

    /**
     * 解析导入的日期：2026-01-05 / 2026-1-5 / 2026/1/5 / 2026.1.5 都接受。
     * 解析不出来返回 null，由调用方决定怎么报错。
     */
    private LocalDate parseImportDate(String text) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        String normalized = text.trim().replace('/', '-').replace('.', '-');
        try {
            return LocalDate.parse(normalized, DateTimeFormatter.ofPattern("uuuu-M-d"));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * 去掉首尾空格并按列宽截断。
     * 不截断的话，超长备注会让 MySQL 抛 1406 Data too long，用户只会看到「系统异常」。
     */
    private String trimToNull(String value, int maxLength) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private long zeroIfNull(Long value) {
        return value == null ? 0L : value;
    }

    private ExpenseRecordVO toVO(ExpenseRecord record) {
        if (record == null) {
            return null;
        }
        ExpenseRecordVO vo = BeanUtil.toBean(record, ExpenseRecordVO.class);
        vo.setAmountCents(Objects.requireNonNullElse(record.getAmountCents(), 0L));
        return vo;
    }
}
