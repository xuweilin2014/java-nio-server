package com.jenkov.nioserver;

import java.nio.ByteBuffer;

/**
 * Created by jjenkov on 16-10-2015.
 */
public class Message {

    private MessageBuffer messageBuffer = null;

    public long socketId = 0; // the id of source socket or destination socket, depending on whether is going in or out.

    public byte[] sharedArray = null;
    public int    offset      = 0; //offset into sharedArray where this message data starts.
    public int    capacity    = 0; //the size of the section in the sharedArray allocated to this message.
    public int    length      = 0; //the number of bytes used of the allocated section.

    public Object metaData    = null;

    public Message(MessageBuffer messageBuffer) {
        this.messageBuffer = messageBuffer;
    }

    /**
     * Writes data from the ByteBuffer into this message - meaning into the buffer backing this message.
     * 将 byteBuffer 中的字节数据保存到 message 中，其实是保存到其 MessageBuffer 中
     *
     * @param byteBuffer The ByteBuffer containing the message data to write.
     */
    public int writeToMessage(ByteBuffer byteBuffer){
        // remaining 等于 limit - position，表示 byteBuffer 中包含的数据字节数
        // 也表示要从 byteBuffer 中读取的字节数
        int remaining = byteBuffer.remaining();

        // this.length 表示当前 message 中包含的数据字节数
        // this.capacity 表示 messageBuffer 分配给这个 message 空间的大小
        // 如果 length 与 remaining 之和大于 capacity，那么说明当前 message 的空间大小需要进行扩展
        // 注意，扩展使用 while 循环，是因为需要扩展多次才能满足新的数据空间要求
        while(this.length + remaining > capacity){
            if(!this.messageBuffer.expandMessage(this)) {
                return -1;
            }
        }

        // todo remaining 似乎一定小于 capacity - length（根据上面 while 循环的条件判断），这个是否可以去掉？
        int bytesToCopy = Math.min(remaining, this.capacity - this.length);
        // 将 byteBuffer 中的全部数据（bytesToCopy 个字节）保存到 sharedArray 中
        byteBuffer.get(this.sharedArray, this.offset + this.length, bytesToCopy);
        this.length += bytesToCopy;

        return bytesToCopy;
    }

    /**
     * Writes data from the byte array into this message - meaning into the buffer backing this message.
     * 将 byteArray 字节数组中的数据写入到当前的 message 中
     *
     * @param byteArray The byte array containing the message data to write.
     * @return
     */
    public int writeToMessage(byte[] byteArray){
        return writeToMessage(byteArray, 0, byteArray.length);
    }

    /**
     * Writes data from the byte array into this message - meaning into the buffer backing this message.
     *
     * @param byteArray The byte array containing the message data to write.
     * @return
     */
    public int writeToMessage(byte[] byteArray, int offset, int length){
        int remaining = length;

        while(this.length + remaining > capacity){
            if(!this.messageBuffer.expandMessage(this)) {
                return -1;
            }
        }

        int bytesToCopy = Math.min(remaining, this.capacity - this.length);
        System.arraycopy(byteArray, offset, this.sharedArray, this.offset + this.length, bytesToCopy);
        this.length += bytesToCopy;
        return bytesToCopy;
    }

    /**
     * In case the buffer backing the nextMessage contains more than one HTTP message, move all data after the first
     * message to a new Message object.
     *
     * @param message   The message containing the partial message (after the first message).
     * @param endIndex  The end index of the first message in the buffer of the message given as parameter.
     */
    public void writePartialMessageToMessage(Message message, int endIndex){
        int startIndexOfPartialMessage = message.offset + endIndex;
        int lengthOfPartialMessage = (message.offset + message.length) - endIndex;

        System.arraycopy(message.sharedArray, startIndexOfPartialMessage, this.sharedArray, this.offset, lengthOfPartialMessage);
    }

    public int writeToByteBuffer(ByteBuffer byteBuffer){
        return 0;
    }

}
