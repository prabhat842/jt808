package com.example.jt808sim.protocol.inbound;

import java.util.List;

/**
 * 0x8302 Question sends down (Tables 42-44, JT808-2013).
 *
 * sign bits (Table 43): bit 0=emergency, bit 3=TTS, bit 4=advertising screen.
 * The simulator auto-answers with the first available answer after a short delay.
 */
public record QuestionSend(int sign, String question, List<AnswerItem> answers) {
    public record AnswerItem(int answerId, String content) {}

    /** Returns the ID of the first available answer, or -1 if no answers provided. */
    public int firstAnswerId() {
        return answers.isEmpty() ? -1 : answers.get(0).answerId();
    }
}
