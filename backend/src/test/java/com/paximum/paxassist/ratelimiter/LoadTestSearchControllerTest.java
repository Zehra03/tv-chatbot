package com.paximum.paxassist.ratelimiter;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LoadTestSearchControllerTest {

    @Test
    void shouldReturnOkForLoadTestSearchEndpoint() {
        LoadTestSearchController controller = new LoadTestSearchController();
        assertEquals("ok", controller.search());
    }
}
