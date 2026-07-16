package com.paximum.paxassist.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the {@link HotelFlightTools} methods as MCP tools. The spring-ai MCP server starter
 * discovers every {@link ToolCallbackProvider} bean and publishes its callbacks over the protocol.
 * Only the search tools are registered here — no booking/reservation tool is ever exposed.
 */
@Configuration
public class McpToolConfig {

    @Bean
    public ToolCallbackProvider searchTools(HotelFlightTools hotelFlightTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(hotelFlightTools)
                .build();
    }
}
