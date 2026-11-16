package anzihe.com.common_template.common;

import java.util.Arrays;
import java.util.List;

/**
 * 记账模块的枚举与边界
 *
 * 分类白名单只在前端做是不够的：接口是公开的，任何脚本都能提交任意分类，
 * 脏数据一旦落库，分类统计就会出现「餐饮」「餐饮 」两条并列的诡异结果。
 * 所以后端统一收敛，不认识的分类一律归到「其他」。
 */
public class ExpenseConstants {

    public static final String TYPE_DAILY = "daily";

    public static final String TYPE_SITE = "site";

    public static final String DEFAULT_CATEGORY = "其他";

    public static final String SOURCE_MANUAL = "manual";

    /** 单笔上限 100 万元，防止手抖多打几个 0 */
    public static final long MAX_AMOUNT_CENTS = 100_000_000L;

    /** 单次导入上限，避免一个请求塞进几十万条把连接池占死 */
    public static final int MAX_IMPORT_SIZE = 500;

    public static final List<String> DAILY_CATEGORIES = Arrays.asList(
            "餐饮", "交通", "购物", "房租", "水电", "娱乐", "学习", "医疗", "其他");

    public static final List<String> SITE_CATEGORIES = Arrays.asList(
            "域名", "服务器", "设计", "开发外包", "插件/工具", "广告推广", "素材", "其他");

    public static boolean isValidType(String type) {
        return TYPE_DAILY.equals(type) || TYPE_SITE.equals(type);
    }

    /**
     * 归一化分类：类型不认识归其他；分类不属于该类型也归其他
     */
    public static String normalizeCategory(String type, String category) {
        String trimmed = category == null ? "" : category.trim();
        List<String> allowed = TYPE_SITE.equals(type) ? SITE_CATEGORIES : DAILY_CATEGORIES;
        return allowed.contains(trimmed) ? trimmed : DEFAULT_CATEGORY;
    }
}
