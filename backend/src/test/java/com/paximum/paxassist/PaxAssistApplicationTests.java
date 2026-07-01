package com.paximum.paxassist;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.ai.chat.model.ChatModel;

@SpringBootTest
@ActiveProfiles("mock")
class PaxAssistApplicationTests {

    @MockitoBean
    private ChatModel chatModel;

    @Test
    void contextLoads() {
    }
}
