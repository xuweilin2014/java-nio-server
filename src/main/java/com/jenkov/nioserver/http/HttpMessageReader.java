package com.jenkov.nioserver.http;

import com.jenkov.nioserver.IMessageReader;
import com.jenkov.nioserver.Message;
import com.jenkov.nioserver.MessageBuffer;
import com.jenkov.nioserver.Socket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by jjenkov on 18-10-2015.
 */
public class HttpMessageReader implements IMessageReader {

    private MessageBuffer messageBuffer = null;
    // completeMessages 表示单个 HTTP 消息的集合
    private List<Message> completeMessages = new ArrayList<Message>();
    // nextMessage 表示从 socket 中读取到的 HTTP 消息，这些 HTTP 消息不一定是完整的
    // 比如有可能是 2.3 个 HTTP 消息，或者 0.4 个 HTTP 消息，但是 nextMessage 类似于
    // 一个缓冲区，不断从客户端接收字节数据，当形成一个完整的 HTTP 消息时，就把这个消息保存到 completeMessages 数组中
    private Message nextMessage = null;

    public HttpMessageReader() {
    }

    @Override
    public void init(MessageBuffer readMessageBuffer) {
        this.messageBuffer = readMessageBuffer;
        this.nextMessage = messageBuffer.getMessage();
        this.nextMessage.metaData = new HttpHeaders();
    }

    @Override
    public void read(Socket socket, ByteBuffer byteBuffer) throws IOException {
        // 将 Socket 中客户端发送过来的数据不断循环读取，bytesRead 表示一共读取到的字节数
        int bytesRead = socket.read(byteBuffer);
        // byteBuffer 刚刚分配时，limit = capacity，position = 0
        // byteBuffer 在调用 flip 函数之后，limit = position，position = 0
        byteBuffer.flip();

        // remaining 返回 limit - position，因此在调用 flip 后，
        // 调用 remaining 返回的是 buffer 从 socket 中读取的字节数
        if(byteBuffer.remaining() == 0){
            byteBuffer.clear();
            return;
        }

        // 将 byteBuffer 中的数据保存到 nextMessage 中
        this.nextMessage.writeToMessage(byteBuffer);

        // 对客户端发送过来的 HTTP 消息进行解析，最终获取到三个数据，contentLength、bodyStartIndex、bodyEndIndex，保存到 httpHeaders
        // contentLength 表示发送过来的 HTTP 消息体的长度、bodyStartIndex、bodyEndIndex 表示消息体开始和结束位置
        int endIndex = HttpUtil.parseHttpRequest(this.nextMessage.sharedArray, this.nextMessage.offset, this.nextMessage.offset + this.nextMessage.length,
                (HttpHeaders) this.nextMessage.metaData);

        if(endIndex != -1){
            Message message = this.messageBuffer.getMessage();
            message.metaData = new HttpHeaders();
            // 在从 socket 读取消息到 byteBuffer 中时，有可能读取了连续多个 HTTP 消息到 byteBuffer 中，
            // 因此创建一个新的 message，将 nextMessage 中存在的除了第一个 HTTP 消息以外的其余 HTTP 消息保存到新的 message 中
            // 使得 nextMessage 只有一个完整的 HTTP 消息
            message.writePartialMessageToMessage(nextMessage, endIndex);
            completeMessages.add(nextMessage);
            nextMessage = message;
        }
        byteBuffer.clear();
    }

    @Override
    public List<Message> getMessages() {
        return this.completeMessages;
    }

}
