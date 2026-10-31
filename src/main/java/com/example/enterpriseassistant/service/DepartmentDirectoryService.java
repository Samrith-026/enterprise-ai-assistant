package com.example.enterpriseassistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class DepartmentDirectoryService {
    private static final Logger log = LoggerFactory.getLogger(DepartmentDirectoryService.class);

    @Cacheable(cacheNames = "departmentContacts", key = "#department", unless = "#result == 'Contact unavailable'")
    public String findContact(String department) {
        // This event occurs only on a cache miss; the tool logs every execution.
        log.info("Department directory cache miss");
        return switch (department) {
            case "HR" -> "hr@company.example";
            case "IT" -> "it-support@company.example";
            case "FINANCE" -> "finance@company.example";
            default -> "Contact unavailable";
        };
    }
}
