package com.jenkov.nioserver;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Created by jjenkov on 24-10-2015.
 */
@SuppressWarnings("FieldCanBeLocal")
public class Server {

    private SocketAccepter  socketAccepter  = null;
    private SocketProcessor socketProcessor = null;

    private int tcpPort = 0;
    private IMessageReaderFactory messageReaderFactory = null;
    private IMessageProcessor     messageProcessor = null;

    public Server(int tcpPort, IMessageReaderFactory messageReaderFactory, IMessageProcessor messageProcessor) {
        this.tcpPort = tcpPort;
        this.messageReaderFactory = messageReaderFactory;
        this.messageProcessor = messageProcessor;
    }

    /**
     * Server 类在启动时，开启两个线程：
     * 1.SocketAcceptor 线程：通过 ServerSocketChannel 来获取客户端对服务器的连接 SocketChannel，并且
     *                       将其包装成 Socket 对象保存到 socketQueue 中
     * 2.SocketProcessor 线程：从 socketQueue 中获取到客户端的连接 SocketChannel，并且从连接读取消息，处理消息，
     *                        最后把响应返回给客户端
     */
    public void start() throws IOException {
        Queue<Socket> socketQueue = new ArrayBlockingQueue<>(1024); // move 1024 to ServerConfig

        this.socketAccepter  = new SocketAccepter(tcpPort, socketQueue);

        MessageBuffer readBuffer  = new MessageBuffer();
        MessageBuffer writeBuffer = new MessageBuffer();

        this.socketProcessor = new SocketProcessor(socketQueue, readBuffer, writeBuffer,  this.messageReaderFactory, this.messageProcessor);

        Thread accepterThread  = new Thread(this.socketAccepter);
        Thread processorThread = new Thread(this.socketProcessor);

        accepterThread.start();
        processorThread.start();
    }


}
