package com.lms.ai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AI 能力服务启动类
 *
 * 职责（spec §2：Agent 运行时 + 三层记忆 + 编排机制）：
 * - 个人 Agent（teacher/student）按用户实例化，多轮对话（L1 会话记忆）
 * - 模块子 Agent（course/exam/learning/search/remark/user/media/statistics/kb/im/task）声明注册 + 工具面
 * - 邀请制互调（SubAgentTool）编排（场景 A/B/C/D）
 * - L2 画像（行为事件 → 摘要）与 L3 个人知识库（lms-kb owner 扩展）
 * - IM 管道（console/wecom）与定时任务（agent_task + @Scheduled 轮询）
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.lms.ai.client")
@MapperScan("com.lms.ai")
@ConfigurationPropertiesScan("com.lms.ai.config")
@EnableScheduling
@EnableAsync
public class AiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiApplication.class, args);
    }
}
