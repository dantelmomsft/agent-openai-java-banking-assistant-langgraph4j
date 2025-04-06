package com.microsoft.openai.samples.assistant.agent;

import java.util.Arrays;
import java.util.List;

public enum Intent {
    BillPayment,
    RepeatTransaction,
    TransactionHistory,
    AccountAgent,
    User;

    /**
     * Returns a list of all possible names of the {@code Intent} enum constants.
     *
     * @return an unmodifiable list containing the names of the {@code Intent} enum constants
     */
    public static List<String> names() {
        return Arrays.stream(Intent.values())
                .map(Enum::name)
                .toList();
    }
}
