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
        // ArrayBlockingQueue 是基于数组的有界阻塞队列，有界指它不能够存储无限多数量的元素，在创建 ArrayBlockingQueue 时，必须要给它
        // 指定一个队列的大小。阻塞指在添加 / 取走元素时，当队列 没有空间 / 为空的时候会阻塞，知道队列有空间 / 有新的元素加入时再继续。
        // 并且 ArrayBlockingQueue 是线程安全的，只有一把锁 lock，线程在往里添加和取出元素时，都需要先获取到锁才行。
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
