package com.lms.calendar;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 统一日历聚合服务（只读）：主页大日历/课表周视图的数据源
 *
 * - 数据所有权留在各域：排课(lms-course schedule_slot)、考试/作业(lms-exam exam_schedule)、活动(预留)；
 * - 本服务按 (userId, 日期区间) 经 Feign 拉各域事件 → 归一 EventVO（type/title/start/end/color/jump）
 *   → Redis 缓存，前端只认 /calendar/mine 一个契约；
 * - 无数据库：lms-common 传递引入 MyBatis-Plus/DataSource 自动配置，此处显式排除（本服务只 Feign + Redis）。
 */
@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        MybatisPlusAutoConfiguration.class
})
@EnableFeignClients(basePackages = "com.lms.calendar.client")
public class CalendarApplication {

    public static void main(String[] args) {
        SpringApplication.run(CalendarApplication.class, args);
    }
}
