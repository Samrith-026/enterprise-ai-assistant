import express from "express";
import { z } from "zod";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";

const app = express();
app.use(express.json({ limit: "64kb" }));

const products = Object.freeze({
    "Employee Portal": Object.freeze({ status: "operational", region: "demo-central", lastUpdated: "2026-11-01T12:00:00Z" }),
    "Payroll Portal": Object.freeze({ status: "degraded", region: "demo-east", lastUpdated: "2026-11-01T12:00:00Z" })
});

app.get("/healthz", (_request, response) => response.json({ status: "UP" }));

app.post("/mcp", async (request, response) => {
    const server = new McpServer({ name: "enterprise-demo-product-service", version: "1.0.0" });
    server.registerTool("get_product_status", {
        title: "Get demo product status",
        description: "Read the synthetic operational status of a known demo product. This tool is read-only and does not call a real external service.",
        inputSchema: { productName: z.enum(["Employee Portal", "Payroll Portal"]) }
    }, async ({ productName }) => {
        const status = products[productName];
        console.info(`MCP tool invoked: get_product_status product=${productName}`);
        return {
            content: [{ type: "text", text: JSON.stringify({ productName, ...status, source: "synthetic-demo-data" }) }]
        };
    });

    const transport = new StreamableHTTPServerTransport({ sessionIdGenerator: undefined });
    try {
        await server.connect(transport);
        await transport.handleRequest(request, response, request.body);
    } catch (error) {
        console.error("MCP request failed:", error instanceof Error ? error.name : "UnknownError");
        if (!response.headersSent) response.status(500).json({ error: "MCP request failed" });
    }
});

app.all("/mcp", (_request, response) => response.status(405).json({ error: "Use POST for MCP requests." }));

app.listen(8090, "0.0.0.0", () => console.info("Demo MCP server listening on port 8090"));
