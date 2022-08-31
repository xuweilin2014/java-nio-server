package com.jenkov.nioserver;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Queue;

/**
 * Created by jjenkov on 19-10-2015.
 */
public class SocketAccepter implements Runnable{

    private final int tcpPort;
    private ServerSocketChannel serverSocket = null;

    private Queue<Socket> socketQueue = null;

    public SocketAccepter(int tcpPort, Queue<Socket> socketQueue)  {
        this.tcpPort = tcpPort;
        this.socketQueue = socketQueue;
    }

    public void run() {
        try{
            // 获取 ServerSocketChannel，并且监听 tcpPort 端口
            this.serverSocket = ServerSocketChannel.open();
            this.serverSocket.bind(new InetSocketAddress(tcpPort));
        } catch(IOException e){
            e.printStackTrace();
            return;
        }

        // SocketAcceptor 线程使用 while 循环，一直监听是否有新的连接 SocketChannel 到来
        // 如果有的话，就将其封装成 Socket 对象，并保存到 SocketQueue 中
        while(true){
            try{
                SocketChannel socketChannel = this.serverSocket.accept();
                System.out.println("Socket accepted: " + socketChannel);
                this.socketQueue.add(new Socket(socketChannel));
            } catch(IOException e){
                e.printStackTrace();
            }

        }

    }
}
