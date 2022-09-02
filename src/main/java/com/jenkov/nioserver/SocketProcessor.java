package com.jenkov.nioserver;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.*;

/**
 * Created by jjenkov on 16-10-2015.
 */
public class SocketProcessor implements Runnable {

    // inboundSocketQueue 中保存了 SocketAcceptor 获取到的客户端连接
    private final Queue<Socket> inboundSocketQueue;

    private MessageBuffer readMessageBuffer = null;
    private MessageBuffer writeMessageBuffer = null;

    private IMessageReaderFactory messageReaderFactory = null;
    // messageProcessor 在处理完客户端发送过来的消息之后，会把响应信息保存到 outboundMessageQueue 中
    private final Queue<Message> outboundMessageQueue = new LinkedList<>();
    private final Map<Long, Socket> socketMap = new HashMap<>();

    private final ByteBuffer readByteBuffer  = ByteBuffer.allocate(1024 * 1024);
    private final ByteBuffer writeByteBuffer = ByteBuffer.allocate(1024 * 1024);
    private final Selector readSelector;
    private final Selector writeSelector;

    private final IMessageProcessor messageProcessor;
    private final WriteProxy writeProxy;

    private long nextSocketId = 16 * 1024; //start incoming socket ids from 16K - reserve bottom ids for pre-defined sockets (servers).

    private final Set<Socket> emptyToNonEmptySockets = new HashSet<>();
    private final Set<Socket> nonEmptyToEmptySockets = new HashSet<>();

    public SocketProcessor(Queue<Socket> inboundSocketQueue, MessageBuffer readMessageBuffer, MessageBuffer writeMessageBuffer, IMessageReaderFactory messageReaderFactory, IMessageProcessor messageProcessor) throws IOException {
        // inboundSocketQueue 队列中保存了客户端连接
        this.inboundSocketQueue = inboundSocketQueue;

        // readMessageBuffer 和 writeMessageBuffer 在整个 Server 中唯一
        this.readMessageBuffer = readMessageBuffer;
        this.writeMessageBuffer = writeMessageBuffer;

        // writeProxy 可以把请求消息 request 处理之后，再保存到 writeProxy 中的 outboundMessageQueue 队列里面，
        // 之后 SocketProcessor 会从 outboundMessageQueue 中读取出响应消息 message，获取对应的 socket 连接，
        // 再通过 messageWriter 把 message 消息通过 socket 返回给客户端
        this.writeProxy = new WriteProxy(writeMessageBuffer, this.outboundMessageQueue);

        // messageReaderFactory 用来生成各种消息解码器，不过这里只提供 HttpReader
        this.messageReaderFactory = messageReaderFactory;
        this.messageProcessor = messageProcessor;
        this.readSelector = Selector.open();
        this.writeSelector = Selector.open();
    }

    public void run() {
        while(true){
            try{
                // executeCycle 主要循环执行以下三种操作：
                // 1.从 inboundSocketQueue 中读取到新客户端连接 Socket
                // 2.从 Socket 中读取消息数据
                // 3.将响应数据写入到 Socket 中返回给客户端
                executeCycle();
            } catch(IOException e){
                e.printStackTrace();
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    public void executeCycle() throws IOException {
        takeNewSockets();
        readFromSockets();
        writeToSockets();
    }


    public void takeNewSockets() throws IOException {
        // Server 中的 SocketAcceptor 线程将 accept 的新连接保存到 inboundSocketQueue 中
        Socket newSocket = this.inboundSocketQueue.poll();

        while(newSocket != null){
            newSocket.socketId = this.nextSocketId++;
            newSocket.socketChannel.configureBlocking(false);

            // 给每一个新建的 socket 连接创建一个 messageReader 和 messageWriter，用来读取请求数据和发送响应
            newSocket.messageReader = this.messageReaderFactory.createMessageReader();
            newSocket.messageReader.init(this.readMessageBuffer);
            newSocket.messageWriter = new MessageWriter();

            this.socketMap.put(newSocket.socketId, newSocket);

            // 将新的客户端连接 socket 注册到 readSelector，并且监听 READ 事件
            SelectionKey key = newSocket.socketChannel.register(this.readSelector, SelectionKey.OP_READ);
            key.attach(newSocket);

            // 继续从 inboundSocketQueue 中读取 socket
            newSocket = this.inboundSocketQueue.poll();
        }
    }


    public void readFromSockets() throws IOException {
        // 使用 selectNow 不阻塞，判断 readSelector 上的连接是否有读事件
        int readReady = this.readSelector.selectNow();

        if(readReady > 0){
            Set<SelectionKey> selectedKeys = this.readSelector.selectedKeys();
            Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

            while(keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();
                // 从客户端 socket 连接上读取数据
                readFromSocket(key);

                keyIterator.remove();
            }
            selectedKeys.clear();
        }
    }

    private void readFromSocket(SelectionKey key) throws IOException {
        Socket socket = (Socket) key.attachment();
        // 从 socket 中读取客户端发送过来的数据，并且将完整的消息保存到 messageReader 中的 completeMessage 数组中
        socket.messageReader.read(socket, this.readByteBuffer);

        List<Message> fullMessages = socket.messageReader.getMessages();
        // 遍历从客户端获取到的每一个完整消息
        if(fullMessages.size() > 0){
            for(Message message : fullMessages){
                message.socketId = socket.socketId;
                // messageProcessor 处理请求消息，然后将得到的响应 response message 保存到 outboundMessageQueue
                // 队列中，后面会依次取出返回给客户端响应
                // the message processor will eventually push outgoing messages into an IMessageWriter for this socket.
                this.messageProcessor.process(message, this.writeProxy);
            }
            fullMessages.clear();
        }

        if(socket.endOfStreamReached){
            System.out.println("Socket closed: " + socket.socketId);
            this.socketMap.remove(socket.socketId);
            key.attach(null);
            key.cancel();
            key.channel().close();
        }
    }


    public void writeToSockets() throws IOException {
        // Take all new messages from outboundMessageQueue
        // 如果 outboundMessageQueue 中有某一个响应消息，那么此消息对应的 socket 就会被保存到 emptyToNonEmptySockets 中
        // 等待被注册到 write selector 上
        takeNewOutboundMessages();

        // Cancel all sockets which have no more data to write.
        cancelEmptySockets();

        // Register all sockets that *have* data and which are not yet registered.
        registerNonEmptySockets();

        // Select from the Selector.
        int writeReady = this.writeSelector.selectNow();

        if(writeReady > 0){
            Set<SelectionKey> selectionKeys = this.writeSelector.selectedKeys();
            Iterator<SelectionKey> keyIterator = selectionKeys.iterator();

            while(keyIterator.hasNext()){
                SelectionKey key = keyIterator.next();
                Socket socket = (Socket) key.attachment();
                // 将这个 writeByteBuffer 中的响应数据通过 socket 发送给客户端
                socket.messageWriter.write(socket, this.writeByteBuffer);
                if(socket.messageWriter.isEmpty()){
                    this.nonEmptyToEmptySockets.add(socket);
                }
                keyIterator.remove();
            }

            selectionKeys.clear();
        }
    }

    private void registerNonEmptySockets() throws ClosedChannelException {
        // 将 emptyToNonEmptySockets 中的 socket 注册到 writeSelector 上，并且监听 WRITE 事件
        for(Socket socket : emptyToNonEmptySockets){
            socket.socketChannel.register(this.writeSelector, SelectionKey.OP_WRITE, socket);
        }
        emptyToNonEmptySockets.clear();
    }

    private void cancelEmptySockets() {
        // 将 nonEmptyToEmptySockets 中的 socket 从 writeSelector 上取消注册
        for(Socket socket : nonEmptyToEmptySockets){
            SelectionKey key = socket.socketChannel.keyFor(this.writeSelector);
            key.cancel();
        }
        nonEmptyToEmptySockets.clear();
    }

    private void takeNewOutboundMessages() {
        // messageProcessor 在处理完客户端请求之后，就会将响应消息保存到 outboundMessageQueue 中
        Message outMessage = this.outboundMessageQueue.poll();

        // 遍历 outboundMessageQueue 队列，取出响应消息
        while(outMessage != null){
            Socket socket = this.socketMap.get(outMessage.socketId);

            if(socket != null){
                MessageWriter messageWriter = socket.messageWriter;
                // 如果 messageWriter 为空，就把响应消息保存到 messageWriter 的队列中
                // 同时，如果此 socket 有响应消息要被发送，就可以将其注册到 write selector 上，一旦此 socket 可以写数据，
                // 那么就把响应消息发送到客户端
                if(messageWriter.isEmpty()){
                    messageWriter.enqueue(outMessage);
                    nonEmptyToEmptySockets.remove(socket);
                    emptyToNonEmptySockets.add(socket);    //not necessary if removed from nonEmptyToEmptySockets in prev. statement.
                } else{
                    messageWriter.enqueue(outMessage);
                }
            }

            outMessage = this.outboundMessageQueue.poll();
        }
    }

}
