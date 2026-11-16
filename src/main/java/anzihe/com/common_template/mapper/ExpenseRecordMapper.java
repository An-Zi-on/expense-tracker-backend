package anzihe.com.common_template.mapper;

import anzihe.com.common_template.model.VO.expense.ExpenseCategoryStatVO;
import anzihe.com.common_template.model.VO.expense.ExpenseDailyStatVO;
import anzihe.com.common_template.model.VO.expense.ExpenseMonthlyStatVO;
import anzihe.com.common_template.model.VO.expense.ExpenseSummaryVO;
import anzihe.com.common_template.model.entity.ExpenseRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;

/**
 * 记账流水 Mapper
 *
 * 统计一律交给数据库做聚合，不要先全量查回 Java 再循环求和：
 * 数据量上万条之后，后者会让看板首屏明显卡顿。
 *
 * 注意：@TableLogic 的逻辑删除只对 MyBatis-Plus 内置方法生效，
 * 手写 SQL 必须自己补 is_delete = 0，否则会把已删记录算进统计。
 */
@Mapper
public interface ExpenseRecordMapper extends BaseMapper<ExpenseRecord> {

    /**
     * 总览统计：一次查询拿到全部看板数字，避免 6 个统计卡片发 6 次请求
     */
    @Select("<script>" +
            "SELECT " +
            "COALESCE(SUM(amount_cents), 0) AS total_cents, " +
            "COALESCE(SUM(CASE WHEN record_date = #{today} THEN amount_cents ELSE 0 END), 0) AS today_cents, " +
            "COALESCE(SUM(CASE WHEN record_date &gt;= #{monthStart} AND record_date &lt;= #{monthEnd} " +
            "  THEN amount_cents ELSE 0 END), 0) AS month_cents, " +
            "COALESCE(SUM(CASE WHEN record_date &gt;= #{yearStart} AND record_date &lt;= #{yearEnd} " +
            "  THEN amount_cents ELSE 0 END), 0) AS year_cents, " +
            "COALESCE(SUM(CASE WHEN expense_type = 'site' THEN amount_cents ELSE 0 END), 0) AS site_total_cents, " +
            "COALESCE(SUM(CASE WHEN expense_type = 'site' AND record_date &gt;= #{monthStart} " +
            "  AND record_date &lt;= #{monthEnd} THEN amount_cents ELSE 0 END), 0) AS site_month_cents, " +
            "COUNT(*) AS record_count, " +
            "COALESCE(SUM(CASE WHEN expense_type = 'site' THEN 1 ELSE 0 END), 0) AS site_record_count " +
            "FROM expense_record " +
            "WHERE user_id = #{userId} AND is_delete = 0" +
            "</script>")
    ExpenseSummaryVO selectSummary(@Param("userId") Long userId,
                                   @Param("today") LocalDate today,
                                   @Param("monthStart") LocalDate monthStart,
                                   @Param("monthEnd") LocalDate monthEnd,
                                   @Param("yearStart") LocalDate yearStart,
                                   @Param("yearEnd") LocalDate yearEnd);

    /**
     * 按分类汇总。type 传 daily / site，传 null 表示不限类型
     */
    @Select("<script>" +
            "SELECT category, " +
            "COALESCE(SUM(amount_cents), 0) AS amount_cents, " +
            "COUNT(*) AS total " +
            "FROM expense_record " +
            "WHERE user_id = #{userId} AND is_delete = 0 " +
            "<if test='type != null and type != \"\"'> AND expense_type = #{type} </if>" +
            "<if test='start != null'> AND record_date &gt;= #{start} </if>" +
            "<if test='end != null'> AND record_date &lt;= #{end} </if>" +
            "GROUP BY category " +
            "ORDER BY amount_cents DESC" +
            "</script>")
    List<ExpenseCategoryStatVO> sumByCategory(@Param("userId") Long userId,
                                              @Param("type") String type,
                                              @Param("start") LocalDate start,
                                              @Param("end") LocalDate end);

    /**
     * 按月汇总，用于趋势图
     */
    @Select("<script>" +
            "SELECT DATE_FORMAT(record_date, '%Y-%m') AS month, " +
            "COALESCE(SUM(amount_cents), 0) AS amount_cents, " +
            "COUNT(*) AS total " +
            "FROM expense_record " +
            "WHERE user_id = #{userId} AND is_delete = 0 " +
            "<if test='type != null and type != \"\"'> AND expense_type = #{type} </if>" +
            "<if test='start != null'> AND record_date &gt;= #{start} </if>" +
            "<if test='end != null'> AND record_date &lt;= #{end} </if>" +
            "GROUP BY DATE_FORMAT(record_date, '%Y-%m') " +
            "ORDER BY month" +
            "</script>")
    List<ExpenseMonthlyStatVO> sumByMonth(@Param("userId") Long userId,
                                          @Param("type") String type,
                                          @Param("start") LocalDate start,
                                          @Param("end") LocalDate end);

    /**
     * 按 id 查询，包含已逻辑删除的记录。
     *
     * 为什么需要它：逻辑删除的记录仍然占着主键。用户删掉一条之后又导入
     * 同一份 CSV（id 相同），如果只查未删除数据会判定「不存在」直接 insert，
     * 撞上主键冲突报 500。先查到删除标记才有机会走「复活」分支。
     */
    @Select("SELECT * FROM expense_record WHERE id = #{id}")
    ExpenseRecord selectByIdIgnoreLogic(@Param("id") String id);

    /**
     * 复活一条被逻辑删除的记录，并用新内容覆盖旧值。
     * 这里必须手写 SQL：MyBatis-Plus 的 update 会自动附加 is_delete = 0 条件，
     * 反而更新不到已删除的那一行。
     */
    @Update("UPDATE expense_record SET user_id = #{userId}, record_date = #{recordDate}, " +
            "amount_cents = #{amountCents}, expense_type = #{expenseType}, category = #{category}, " +
            "project = #{project}, payment = #{payment}, note = #{note}, source = #{source}, " +
            "is_delete = 0 WHERE id = #{id}")
    int revive(ExpenseRecord record);

    /**
     * 按天汇总，供看板日历使用。
     *
     * 只返回「有消费的日期」，没有数据的日期不占位：一个月最多 31 行，
     * 让 Java 补齐 31 个空对象再传到前端，不如前端按日期做 Map 匹配来得直接。
     */
    @Select("<script>" +
            "SELECT DATE_FORMAT(record_date, '%Y-%m-%d') AS day, " +
            "COALESCE(SUM(amount_cents), 0) AS amount_cents, " +
            "COUNT(*) AS total " +
            "FROM expense_record " +
            "WHERE user_id = #{userId} AND is_delete = 0 " +
            "AND record_date &gt;= #{start} AND record_date &lt;= #{end} " +
            "GROUP BY DATE_FORMAT(record_date, '%Y-%m-%d') " +
            "ORDER BY day" +
            "</script>")
    List<ExpenseDailyStatVO> sumByDay(@Param("userId") Long userId,
                                      @Param("start") LocalDate start,
                                      @Param("end") LocalDate end);

    /**
     * 当前账号的有效账单条数。清空/初始化前用它判断「是不是已经有数据」。
     * 和导出一样自己补 is_delete = 0，@TableLogic 管不到手写 SQL。
     */
    @Select("SELECT COUNT(*) FROM expense_record WHERE user_id = #{userId} AND is_delete = 0")
    long countByUser(@Param("userId") Long userId);

    /**
     * 清空某个账号的全部账单。
     *
     * 走逻辑删除而不是 DELETE：删错了还能从库里捞回来，
     * 而且统计 SQL 本来就带了 is_delete = 0，不需要改任何查询。
     * 返回受影响行数，前端用它告诉用户「清掉了多少条」。
     */
    @Update("UPDATE expense_record SET is_delete = 1 WHERE user_id = #{userId} AND is_delete = 0")
    int clearByUser(@Param("userId") Long userId);
}
