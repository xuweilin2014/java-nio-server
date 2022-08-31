package com.jenkov.nioserver;

/**
 * A shared buffer which can contain many messages inside. A message gets a section of the buffer to use. If the
 * message outgrows the section in size, the message requests a larger section and the message is copied to that
 * larger section. The smaller section is then freed again.
 *
 * 在这个 NIO Server 中 MessageBuffer 是真正用来存储客户端发送过来的消息，它把发送过来的消息分为 small、medium 以及 large 三个
 * 层次，分别表示 4KB、128KB、1MB 大小。给 small 类的消息分配了 1024 个 section，给 medium 类的消息分配了 128 个 section，
 * 给 large 类的消息分配了 16 个 section。
 *
 * 在分配每一类的 section 时，需要对已经分配出去的内存块进行记录。这里使用的是 QueueIntFlip 类来保存已经分配出去的 section 的起始
 * 地址。QueueIntFlip 内部有一个 int 数组 elements 来进行记录，这个 QueueIntFlip 使用类似于生产者消费者的模式。在其初始化时，就
 * 会填充满所有 section 的地址，当有 message 到达需要分配一个 section 进行存储时，就会从 QueueIntFlip 中 take 一个 freeBlock
 * Address（消费者），当 message 处理完成之后，就调用 put 方法将其归还到 QueueIntFlip 中（生产者）。
 */
@SuppressWarnings("PointlessArithmeticExpression")
public class MessageBuffer {

    public static int KB = 1024;
    public static int MB = 1024 * 1024;

    private static final int CAPACITY_SMALL = 4 * KB;
    private static final int CAPACITY_MEDIUM = 128 * KB;
    private static final int CAPACITY_LARGE = 1024 * KB;

    //package scope (default) - so they can be accessed from unit tests.
    byte[] smallMessageBuffer = new byte[1024 * 4 * KB];   //1024 x   4KB messages =  4MB.
    byte[] mediumMessageBuffer = new byte[128 * 128 * KB];   // 128 x 128KB messages = 16MB.
    byte[] largeMessageBuffer = new byte[16 * 1 * MB];   //  16 *   1MB messages = 16MB.

    QueueIntFlip smallMessageBufferFreeBlocks = new QueueIntFlip(1024); // 1024 free sections
    QueueIntFlip mediumMessageBufferFreeBlocks = new QueueIntFlip(128);  // 128  free sections
    QueueIntFlip largeMessageBufferFreeBlocks = new QueueIntFlip(16);   // 16   free sections

    public MessageBuffer() {
        // 分别初始化 small、medium、large 这三个层次对应的 QueueIntFlip，即它们 section 对应的初始地址
        // add all free sections to all free section queues.
        for(int i=0; i<smallMessageBuffer.length; i+= CAPACITY_SMALL){
            this.smallMessageBufferFreeBlocks.put(i);
        }
        for(int i=0; i<mediumMessageBuffer.length; i+= CAPACITY_MEDIUM){
            this.mediumMessageBufferFreeBlocks.put(i);
        }
        for(int i=0; i<largeMessageBuffer.length; i+= CAPACITY_LARGE){
            this.largeMessageBufferFreeBlocks.put(i);
        }
    }

    public Message getMessage() {
        int nextFreeSmallBlock = this.smallMessageBufferFreeBlocks.take();

        if(nextFreeSmallBlock == -1) return null;

        Message message = new Message(this);

        message.sharedArray = this.smallMessageBuffer;
        message.capacity    = CAPACITY_SMALL;
        message.offset      = nextFreeSmallBlock;
        message.length      = 0;

        return message;
    }

    public boolean expandMessage(Message message){
        if(message.capacity == CAPACITY_SMALL){
            return moveMessage(message, this.smallMessageBufferFreeBlocks, this.mediumMessageBufferFreeBlocks, this.mediumMessageBuffer, CAPACITY_MEDIUM);
        } else if(message.capacity == CAPACITY_MEDIUM){
            return moveMessage(message, this.mediumMessageBufferFreeBlocks, this.largeMessageBufferFreeBlocks, this.largeMessageBuffer, CAPACITY_LARGE);
        } else {
            return false;
        }
    }

    private boolean moveMessage(Message message, QueueIntFlip srcBlockQueue, QueueIntFlip destBlockQueue, byte[] dest, int newCapacity) {
        int nextFreeBlock = destBlockQueue.take();
        if(nextFreeBlock == -1) return false;

        System.arraycopy(message.sharedArray, message.offset, dest, nextFreeBlock, message.length);

        srcBlockQueue.put(message.offset); //free smaller block after copy

        message.sharedArray = dest;
        message.offset = nextFreeBlock;
        message.capacity = newCapacity;
        return true;
    }

}
