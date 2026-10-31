package com.example.enterpriseassistant.tool;

import java.util.Locale;

import com.example.enterpriseassistant.service.DepartmentDirectoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class DepartmentContactTool {
    private static final Logger log = LoggerFactory.getLogger(DepartmentContactTool.class);
    private final DepartmentDirectoryService directory;

    public DepartmentContactTool(DepartmentDirectoryService directory) {
        this.directory = directory;
    }

    @Tool(description = "Get the enterprise contact email for a department: HR, IT, or Finance. Read-only lookup.")
    public String getDepartmentContact(@ToolParam(description = "Department name: HR, IT, or Finance") String department) {
        log.info("Executing getDepartmentContact tool");
        String normalized = department == null ? "" : department.strip().toUpperCase(Locale.ROOT);
        return directory.findContact(normalized);
    }
}
