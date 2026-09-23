package com.tcpchat.common;

import com.google.gson.Gson;
import com.tcpchat.common.model.FilePacket;
import com.tcpchat.common.model.Message;
import com.tcpchat.common.model.TextResult;

public class MessageParser {
    private static final Gson gson = new Gson();

    public static String toJson(Object object) {
        return gson.toJson(object);
    }

    public static Message parseMessage(String json) {
        return gson.fromJson(json, Message.class);
    }

    public static FilePacket parseFilePacket(String json) {
        return gson.fromJson(json, FilePacket.class);
    }

    public static TextResult parseTextResult(String json) {
        return gson.fromJson(json, TextResult.class);
    }
}
