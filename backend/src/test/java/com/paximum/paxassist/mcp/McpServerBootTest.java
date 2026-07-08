package com.paximum.paxassist.mcp;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the full application context (H2 + mock TourVisio + mock AI, no external services) to prove
 * the MCP server auto-configuration integrates without breaking startup, and that the registered
 * MCP surface is exactly the two SEARCH tools — enforcing the security boundary "no booking tool".
 */
@SpringBootTest
@ActiveProfiles({"loadtest", "mock", "mock-ai"})
class McpServerBootTest {

    @Autowired
    private ToolCallbackProvider searchTools;

    @Test
    void exposesOnlySearchToolsAndNeverBooking() {
        List<String> names = Arrays.stream(searchTools.getToolCallbacks())
                .map(callback -> callback.getToolDefinition().name())
                .toList();

        assertThat(names).containsExactlyInAnyOrder("searchHotels", "searchFlights");
        assertThat(names).noneMatch(name -> {
            String lower = name.toLowerCase();
            return lower.contains("book") || lower.contains("reserv");
        });
    }
}
