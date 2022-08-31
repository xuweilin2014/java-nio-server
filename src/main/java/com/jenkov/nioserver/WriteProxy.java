package com.jenkov.nioserver;

import java.util.Queue;

/**
 * Created by jjenkov on 22-10-2015.
 */
public class WriteProxy {

    private MessageBuffer messageBuffer = null;
    private Queue<Message> writeQueue = null;

    public WriteProxy(MessageBuffer messageBuffer, Queue<Message> writeQueue) {
        this.messageBuffer = messageBuffer;
        this.writeQueue = writeQueue;
    }

    public Message getMessage(){
        return this.messageBuffer.getMessage();
    }

    public boolean enqueue(Message message){
        return this.writeQueue.offer(message);
    }

}
