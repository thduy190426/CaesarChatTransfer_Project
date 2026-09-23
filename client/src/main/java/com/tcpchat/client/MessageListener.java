package com.tcpchat.client;

import com.tcpchat.common.model.FilePacket;
import com.tcpchat.common.model.Message;
import com.tcpchat.common.model.TextResult;

public interface MessageListener {
    void onTextResultReceived(TextResult result);
    void onFileAckReceived(FilePacket ack);
    void onErrorReceived(Message error);
    void onDisconnected();
}
