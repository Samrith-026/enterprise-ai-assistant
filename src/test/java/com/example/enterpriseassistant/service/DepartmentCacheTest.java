package com.example.enterpriseassistant.service;

import com.example.enterpriseassistant.tool.DepartmentContactTool;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DepartmentCacheTest {
    @Test
    void toolNormalizesKeysAndCachingProxyAvoidsRepeatedLookup() {
        // In-process cache verifies annotation/proxy behavior without requiring Redis.
        try (var context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            var directory = context.getBean(DepartmentDirectoryService.class);
            var tool = new DepartmentContactTool(directory);
            assertThat(tool.getDepartmentContact(" hr ")).isEqualTo("hr@company.example");
            assertThat(tool.getDepartmentContact("HR")).isEqualTo("hr@company.example");
            assertThat(context.getBean(CacheManager.class).getCache("departmentContacts").get("HR", String.class))
                    .isEqualTo("hr@company.example");
            assertThat(tool.getDepartmentContact("unknown")).isEqualTo("Contact unavailable");
            assertThat(context.getBean(CacheManager.class).getCache("departmentContacts").get("UNKNOWN")).isNull();
            var target = org.springframework.test.util.AopTestUtils.<DepartmentDirectoryService>getUltimateTargetObject(directory);
            verify(target, times(1)).findContact("HR");
        }
    }

    @Configuration
    @EnableCaching
    static class TestConfig {
        @Bean
        CacheManager cacheManager() { return new ConcurrentMapCacheManager("departmentContacts"); }
        @Bean
        DepartmentDirectoryService directory() { return spy(new DepartmentDirectoryService()); }
    }
}
