package com.lms.common.autoconfigure.swagger;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Knife4j / OpenAPI3 文档配置
 * <p>各业务服务在 application.yml 中配置 springdoc 分组/路径即可
 */
@AutoConfiguration
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
