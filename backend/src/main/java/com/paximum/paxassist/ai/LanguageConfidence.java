package com.paximum.paxassist.ai;

/**
 * How sure the intent model is about the language it detected for the current user message.
 *
 * <p>{@link #LOW} marks a message too short or too language-neutral to tell (only a number, an
 * emoji, a bare name/city). On {@code LOW} the reply language should stay whatever the previous
 * turn used rather than switch on a guess — so a lone "2" never flips the conversation's language.
 */
public enum LanguageConfidence {
    HIGH,
    LOW
}
