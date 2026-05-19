package com.example.jt808.platform.gateway;

record CommandDispatchResult(
        boolean accepted,
        String rtvsReturnValue,
        String commandId,
        int messageId,
        int sequence,
        String error
) {
    static CommandDispatchResult accepted(String commandId, int messageId, int sequence) {
        String value = (messageId == com.example.jt808.platform.protocol.MessageIds.JT1078_PLAYBACK_REQUEST
                || messageId == com.example.jt808.platform.protocol.MessageIds.JT1078_QUERY_RESOURCE_LIST)
                ? commandId
                : "1";
        return new CommandDispatchResult(true, value, commandId, messageId, sequence, "");
    }

    static CommandDispatchResult failure(String rtvsReturnValue, String error) {
        return new CommandDispatchResult(false, rtvsReturnValue, "", 0, 0, error);
    }
}
