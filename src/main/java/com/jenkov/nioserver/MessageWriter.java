package com.jenkov.nioserver;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by jjenkov on 21-10-2015.
 */
public class MessageWriter {

    private List<Message> writeQueue   = new ArrayList<>();
    private Message  messageInProgress = null;
    private int bytesWritten = 0;

    public MessageWriter() {
    }

    public void enqueue(Message message) {
        if(this.messageInProgress == null){
            this.messageInProgress = message;
        } else {
            this.writeQueue.add(message);
        }
    }

    public void write(Socket socket, ByteBuffer byteBuffer) throws IOException {
        // put 方法将 messageInProgress 中的数据写入到 byteBuffer 中
        byteBuffer.put(this.messageInProgress.sharedArray, this.messageInProgress.offset + this.bytesWritten, this.messageInProgress.length - this.bytesWritten);
        byteBuffer.flip();

        // bytesWritten 表示从 byteBuffer 中写入到 socket 连接中的字节数
        this.bytesWritten += socket.write(byteBuffer);
        byteBuffer.clear();

        // 如果此 message 写入到 socket 中的字节数大于 length，说明这个 message 基本全部被写入到 socket 中
        // 因此，如果 writeQueue 中还有其他 message，就将 messageInProgress 指向它
        if(bytesWritten >= this.messageInProgress.length){
            if(this.writeQueue.size() > 0){
                this.messageInProgress = this.writeQueue.remove(0);
            } else {
                this.messageInProgress = null;
            }
        }
    }

    public boolean isEmpty() {
        return this.writeQueue.isEmpty() && this.messageInProgress == null;
    }

}
