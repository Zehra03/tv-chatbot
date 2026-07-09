package com.paximum.paxassist.chat.service;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.paximum.paxassist.chat.domain.ChatSession;
import com.paximum.paxassist.chat.dto.ChoiceOptionDto;
import com.paximum.paxassist.chat.dto.SendMessageResponseDto;
import com.paximum.paxassist.orchestrator.ChoiceOption;
import com.paximum.paxassist.orchestrator.OrchestrationResult;

import static org.assertj.core.api.Assertions.assertThat;

class ChatResponseAssemblerTest {

    private final ChatResponseAssembler assembler = new ChatResponseAssembler(new ChatViewMapper());

    @Test
    void mapsDisambiguationOptionsOntoTheReply() {
        OrchestrationResult result = OrchestrationResult
                .choices("Otel araması mı yoksa uçuş araması mı yapmak istersiniz?", List.of(
                        new ChoiceOption("Otel ara", "Antalya için otel arıyorum"),
                        new ChoiceOption("Uçuş ara", "Antalya için uçuş arıyorum")))
                .withSessionId("42");

        SendMessageResponseDto response = assembler.toResponse(result, new ChatSession("42"));

        assertThat(response.reply().content())
                .isEqualTo("Otel araması mı yoksa uçuş araması mı yapmak istersiniz?");
        assertThat(response.reply().cards()).isNull();
        assertThat(response.pendingQuestion()).isNull();
        assertThat(response.reply().options())
                .extracting(ChoiceOptionDto::label)
                .containsExactly("Otel ara", "Uçuş ara");
        assertThat(response.reply().options())
                .extracting(ChoiceOptionDto::value)
                .containsExactly("Antalya için otel arıyorum", "Antalya için uçuş arıyorum");
    }

    @Test
    void leavesOptionsNullForANonChoicesReply() {
        OrchestrationResult result = OrchestrationResult.message("Merhaba!").withSessionId("42");

        SendMessageResponseDto response = assembler.toResponse(result, new ChatSession("42"));

        assertThat(response.reply().options()).isNull();
        assertThat(response.reply().cards()).isNull();
    }
}
