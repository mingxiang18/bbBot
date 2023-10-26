package com.bb.onebot.event;

import org.springframework.context.ApplicationEvent;

/**
 * WebSocket服务器连接事件
 * @author ren
 */
public class WebSocketConnectEvent extends ApplicationEvent {

    private boolean connectionStatus;

    public WebSocketConnectEvent(boolean connectionStatus) {
        super(connectionStatus);
        this.connectionStatus = connectionStatus;
    }

    public boolean connectionStatus() {
        return connectionStatus;
    }
}
