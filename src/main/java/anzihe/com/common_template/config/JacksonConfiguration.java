package anzihe.com.common_template.config;

import anzihe.com.common_template.utils.DateTimeFormatterUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.ext.javatime.deser.LocalDateDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalTimeDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalTimeSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Spring Boot 4 起默认使用 Jackson 3（tools.jackson.*）：
 * 原先的 Jackson2ObjectMapperBuilderCustomizer 已被 JsonMapperBuilderCustomizer 取代，
 * 且 WRITE_DATES_AS_TIMESTAMPS 等日期特性从 SerializationFeature 迁移到了 DateTimeFeature。
 *
 * <p>这里只处理「配置属性表达不了」的部分：java.time 的全局格式。
 * 时区、Locale 仍由 application.yml 的 spring.jackson.* 提供。
 */
@Slf4j
@Configuration
public class JacksonConfiguration {

    @Bean
    public JsonMapperBuilderCustomizer javaTimeCustomizer() {
        return builder -> {
            SimpleModule javaTimeModule = new SimpleModule("common-template-java-time");

            // 序列化：统一输出 yyyy-MM-dd HH:mm:ss / yyyy-MM-dd / HH:mm:ss
            javaTimeModule.addSerializer(LocalDateTime.class,
                    new LocalDateTimeSerializer(DateTimeFormatterUtil.DATE_TIME_FORMAT));
            javaTimeModule.addSerializer(LocalDate.class,
                    new LocalDateSerializer(DateTimeFormatterUtil.DATE_FORMAT));
            javaTimeModule.addSerializer(LocalTime.class,
                    new LocalTimeSerializer(DateTimeFormatterUtil.TIME_FORMAT));

            // 反序列化：接受同样的格式
            javaTimeModule.addDeserializer(LocalDateTime.class,
                    new LocalDateTimeDeserializer(DateTimeFormatterUtil.DATE_TIME_FORMAT));
            javaTimeModule.addDeserializer(LocalDate.class,
                    new LocalDateDeserializer(DateTimeFormatterUtil.DATE_FORMAT));
            javaTimeModule.addDeserializer(LocalTime.class,
                    new LocalTimeDeserializer(DateTimeFormatterUtil.TIME_FORMAT));

            builder.addModule(javaTimeModule);

            // 日期一律输出为格式化字符串，而不是时间戳
            builder.disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS);
            // 空对象不抛异常
            builder.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
            // 反序列化遇到未知字段不报错
            builder.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

            log.info("Jackson 3 全局配置已加载: LocalDateTime={}, LocalDate={}, LocalTime={}",
                    DateTimeFormatterUtil.DATE_TIME_STR,
                    DateTimeFormatterUtil.DATE_STR,
                    DateTimeFormatterUtil.TIME_STR);
        };
    }
}
