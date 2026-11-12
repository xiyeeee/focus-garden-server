package com.xiye.garden.config;

import java.util.Arrays;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class AuthEnvironmentGuard {
    public AuthEnvironmentGuard(Environment environment) {
        var profiles = Arrays.asList(environment.getActiveProfiles());
        if (profiles.contains("prod") && (profiles.contains("local-mail") || profiles.contains("integration"))) {
            throw new IllegalStateException("生产环境不能启用本地邮件或集成测试配置");
        }
    }
}
