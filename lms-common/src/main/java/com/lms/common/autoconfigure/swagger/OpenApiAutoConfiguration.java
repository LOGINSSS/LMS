package com.lms.common.autoconfigure.swagger;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Knife4j / OpenAPI3 文档配置
 *
 * 各业务服务在 application.yml 中配置 springdoc 分组/路径即可。
 * 条件注解 @ConditionalOnClass：classpath 无 springdoc（如网关）时自动跳过，
 * 保证公共库可被任意模块安全引入。
 */
@AutoConfiguration
@ConditionalOnClass(OpenAPI.class)
public class OpenApiAutoConfiguration {

    @Bean
    public OpenAPI lmsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("LMS API")
                        .description("LMS 微服务练手项目接口文档")
                        .version("1.0.0"));
    }
}
