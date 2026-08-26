package com.lms.common.autoconfigure.mvc;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.lms.common.utils.DateUtils;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

/**
 * MVC 自动配置：全局异常处理 + Jackson 时间格式统一 + 用户头拦截器（按配置开关）
 */
@AutoConfiguration
@Import({CommonExceptionAdvice.class, UserInfoInterceptorConfiguration.class})
public class MvcAutoConfiguration {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer lmsJacksonCustomizer() {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(DateUtils.DEFAULT_DATE_TIME_FORMAT);
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(DateUtils.DEFAULT_DATE_FORMAT);
        return builder -> builder
                .timeZone(TimeZone.getTimeZone("GMT+8"))
                .serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(dateTimeFormatter))
                .deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer(dateTimeFormatter))
                .serializerByType(LocalDate.class, new LocalDateSerializer(dateFormatter))
                .deserializerByType(LocalDate.class, new LocalDateDeserializer(dateFormatter));
    }
}
