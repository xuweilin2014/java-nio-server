package com.jenkov.nioserver;

/**
 * Same as QueueFillCount, except that QueueFlip uses a flip flag to keep track of when the internal writePos has
 * "overflowed" (meaning it goes back to 0). Other than that, the two implementations are very similar in functionality.
 *
 * One additional difference is that QueueFlip has an available() method, where this is a public variable in
 * QueueFillCount.
 *
 * 在 MessageBuffer 中使用了三个字节数组（smallMessageBuffer、mediumMessageBuffer 以及 largeMessageBuffer）来实际存储
 * 从客户端读取的消息字节数据。其中 smallMessageBuffer 中可以保存 1024 个 4KB 的消息，mediumMessageBuffer 中可以保存 128
 * 个 128KB 的消息，largeMessageBuffer 可以保存 16 个 1MB 的消息。
 *
 * 以 smallMessageBuffer 为例，其中的 1024 个 section 的地址起始位置保存在 QueueIntFlip 的 elements 数组中，比如 0, 1024, 2048
 * 等。QueueIntFlip 中的 elements 数组可以看成是一个生产者消费者模式。put 方法是生产者，也就是将 smallMessageBuffer 可用 section
 * 的 offset 起始地址保存到 elements 中，并移动 writePos 指标；take 方法是消费者，从 elements 中取出可用的起始地址，移动 readPos。
 *
 * 在 QueueIntFlip 中，只循环使用一个 elements 数组：
 * 1.当 flipped 为 false 时，就是正常的 writePos > readPos，其中 writePos 就表示生产者放入了多少库存，readPos 表示消费了多少库存
 * 2.当 flipped 为 true 时，这 writePos < readPos
 */
public class QueueIntFlip {

    public int[] elements = null;

    public int capacity = 0;
    public int writePos = 0;
    public int readPos  = 0;
    public boolean flipped = false;

    public QueueIntFlip(int capacity) {
        this.capacity = capacity;
        this.elements = new int[capacity];
    }

    public void reset() {
        this.writePos = 0;
        this.readPos  = 0;
        this.flipped  = false;
    }

    // available 方法返回 elements 中还有多少库存，即多少可以分配的 section
    public int available() {
        if(!flipped){
            return writePos - readPos;
        }
        return capacity - readPos + writePos;
    }

    // remainingCapacity 方法返回 elements 中还有多少容量可以来存放 section 的地址
    public int remainingCapacity() {
        if(!flipped){
            return capacity - writePos + readPos;
        }
        return readPos - writePos;
    }

    // element 表示 section 的起始地址，put 方法将起始地址保存到 elements 数组中，相当于生产者
    public boolean put(int element){
        // flipped = false 时，writePos > readPos
        if(!flipped){
            if(writePos == capacity){
                writePos = 0;
                flipped = true;

                // 当 flipped 为 true 时，就要 w 必须小于 r，因为 r 表示消费者消费了多少，
                // 如果 w == r 时，就说明现在 elements 数组中没有库存，不能再保存 element，
                // 因此直接返回 false
                if(writePos < readPos){
                    elements[writePos++] = element;
                    return true;
                } else {
                    return false;
                }
            // 当 flipped = false 时，w > r，w 可以直接增加直到等于 capacity
            } else {
                elements[writePos++] = element;
                return true;
            }
        } else {
            if(writePos < readPos){
                elements[writePos++] = element;
                return true;
            } else {
                return false;
            }
        }
    }

    public int put(int[] newElements, int length){
        int newElementsReadPos = 0;
        if(!flipped){
            //readPos lower than writePos - free sections are:
            //1) from writePos to capacity
            //2) from 0 to readPos

            if(length <= capacity - writePos){
                //new elements fit into top of elements array - copy directly
                for(; newElementsReadPos < length; newElementsReadPos++){
                    this.elements[this.writePos++] = newElements[newElementsReadPos];
                }

                return newElementsReadPos;
            } else {
                //new elements must be divided between top and bottom of elements array

                //writing to top
                for(;this.writePos < capacity; this.writePos++){
                    this.elements[this.writePos] = newElements[newElementsReadPos++];
                }

                //writing to bottom
                this.writePos = 0;
                this.flipped  = true;
                int endPos = Math.min(this.readPos, length - newElementsReadPos);
                for(; this.writePos < endPos; this.writePos++){
                    this.elements[writePos] = newElements[newElementsReadPos++];
                }

                return newElementsReadPos;
            }
        } else {
            //readPos higher than writePos - free sections are:
            //1) from writePos to readPos

            int endPos = Math.min(this.readPos, this.writePos + length);

            for(; this.writePos < endPos; this.writePos++){
                this.elements[this.writePos] = newElements[newElementsReadPos++];
            }

            return newElementsReadPos;
        }
    }

    // 从 elements 数组中获取一个空闲的 section，返回的是这个空闲 section 的起始位置
    public int take() {
        // flipped = false，就是正常的 r < w
        if(!flipped){
            // writePos 的位置表示现在 elements 数组中还有多少个空闲 section
            // 所以 readPos 不能大于 writePos
            if(readPos < writePos){
                return elements[readPos++];
            } else {
                return -1;
            }
        // flipped = true，就是 w < r
        } else {
            if(readPos == capacity){
                readPos = 0;
                flipped = false;

                if(readPos < writePos){
                    return elements[readPos++];
                } else {
                    return -1;
                }
            } else {
                return elements[readPos++];
            }
        }
    }

    public int take(int[] into, int length){
        int intoWritePos = 0;
        if(!flipped){
            //writePos higher than readPos - available section is writePos - readPos

            int endPos = Math.min(this.writePos, this.readPos + length);
            for(; this.readPos < endPos; this.readPos++){
                into[intoWritePos++] = this.elements[this.readPos];
            }
            return intoWritePos;
        } else {
            //readPos higher than writePos - available sections are top + bottom of elements array

            if(length <= capacity - readPos){
                //length is lower than the elements available at the top of the elements array - copy directly
                for(; intoWritePos < length; intoWritePos++){
                    into[intoWritePos] = this.elements[this.readPos++];
                }

                return intoWritePos;
            } else {
                //length is higher than elements available at the top of the elements array
                //split copy into a copy from both top and bottom of elements array.

                //copy from top
                for(; this.readPos < capacity; this.readPos++){
                    into[intoWritePos++] = this.elements[this.readPos];
                }

                //copy from bottom
                this.readPos = 0;
                this.flipped = false;
                int endPos = Math.min(this.writePos, length - intoWritePos);
                for(; this.readPos < endPos; this.readPos++){
                    into[intoWritePos++] = this.elements[this.readPos];
                }

                return intoWritePos;
            }
        }
    }

}
