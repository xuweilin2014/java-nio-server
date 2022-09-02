package com.jenkov.nioserver.http;

import java.io.UnsupportedEncodingException;

/**
 * Created by jjenkov on 19-10-2015.
 */
public class HttpUtil {

    private static final byte[] GET = new byte[]{'G','E','T'};
    private static final byte[] POST = new byte[]{'P','O','S','T'};
    private static final byte[] PUT = new byte[]{'P','U','T'};
    private static final byte[] HEAD = new byte[]{'H','E','A','D'};
    private static final byte[] DELETE = new byte[]{'D','E','L','E','T','E'};

    private static final byte[] HOST = new byte[]{'H','o','s','t'};
    private static final byte[] CONTENT_LENGTH = new byte[]{'C','o','n','t','e','n','t','-','L','e','n','g','t','h'};

    /**
     * 客户端发送一个 HTTP 请求到服务器的请求消息包括以下格式：请求行、请求头部、空行以及请求数据四个部分组成
     * 请求报文的一般格式如下：
     *
     * （请求行）请求方法 | 空格 | URL | 空格 | 协议版本 | 回车符 | 换行符
     * （请求头部）头部字段名：值 | 回车符 | 换行符
     * ......
     * （请求头部）头部字段名：值 | 回车符 | 换行符
     * （空行）回车符 | 换行符
     * （请求数据）请求数据
     *
     * @param src：src 为服务器接收到的 http 消息字节数据
     * @param startIndex：http 消息字节数据的开始索引
     * @param endIndex：http 消息字节数据的结束索引
     * @param httpHeaders
     * @return
     */
    public static int parseHttpRequest(byte[] src, int startIndex, int endIndex, HttpHeaders httpHeaders){

        // parse HTTP request line
        // 由于 http 请求行与请求头部都是以回车符和换行符结尾，所以 findNextLineBreak 方法返回每一行中 \n 字符的位置
        int endOfFirstLine = findNextLineBreak(src, startIndex, endIndex);
        if(endOfFirstLine == -1) return -1;


        // parse HTTP headers
        int prevEndOfHeader = endOfFirstLine + 1;
        int endOfHeader = findNextLineBreak(src, prevEndOfHeader, endIndex);

        // 由于请求头部可能有多行，因此需要循环来找到 Content-Length 字段，并且获取到 Content-Length 的长度。
        // 同时，还要最终获取到请求头部和空行的结束位置 endOfHeader
        while(endOfHeader != -1 && endOfHeader != prevEndOfHeader + 1){    //prevEndOfHeader + 1 = end of previous header + 2 (+2 = CR + LF)

            if(matches(src, prevEndOfHeader, CONTENT_LENGTH)){
                try {
                    findContentLength(src, prevEndOfHeader, endIndex, httpHeaders);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }

            prevEndOfHeader = endOfHeader + 1;
            endOfHeader = findNextLineBreak(src, prevEndOfHeader, endIndex);
        }

        if(endOfHeader == -1){
            return -1;
        }

        // 根据 endOfHeader 以及前面的 content-length 字段值，最终得到请求消息体的 bodyStartIndex 和 bodyEndIndex
        // check that byte array contains full HTTP message.
        int bodyStartIndex = endOfHeader + 1;
        int bodyEndIndex  = bodyStartIndex + httpHeaders.contentLength;

        // endIndex 是发送过来的消息字节的结束 index，而 bodyEndIndex 是消息中第一个 HTTP 消息体的结束 index
        // 由于 src 中可能包含多个 HTTP 消息，因此 bodyEndIndex < endIndex
        if(bodyEndIndex <= endIndex){
            // byte array contains a full HTTP request
            httpHeaders.bodyStartIndex = bodyStartIndex;
            httpHeaders.bodyEndIndex   = bodyEndIndex;
            return bodyEndIndex;
        }

        return -1;
    }

    /**
     * src 为请求头部的某一行，并且包含 Content-Length 字段，最终返回 Content-Length 字段的值
     */
    private static void findContentLength(byte[] src, int startIndex, int endIndex, HttpHeaders httpHeaders) throws UnsupportedEncodingException {
        // 找到冒号 ：的位置
        int indexOfColon = findNext(src, startIndex, endIndex, (byte) ':');

        // skip spaces after colon
        // 跳过冒号后面的空白字符
        int index = indexOfColon + 1;
        while(src[index] == ' '){
            index++;
        }

        int valueStartIndex = index;
        int valueEndIndex = index;
        boolean endOfValueFound = false;

        while(index < endIndex && !endOfValueFound){
            switch(src[index]){
                case '0' : ;
                case '1' : ;
                case '2' : ;
                case '3' : ;
                case '4' : ;
                case '5' : ;
                case '6' : ;
                case '7' : ;
                case '8' : ;
                case '9' : { index++;  break; }

                default: {
                    endOfValueFound = true;
                    valueEndIndex = index;
                }
            }
        }

        httpHeaders.contentLength = Integer.parseInt(new String(src, valueStartIndex, valueEndIndex - valueStartIndex, "UTF-8"));
    }

    // 在字节数组 src 中找到字节 value 的位置
    public static int findNext(byte[] src, int startIndex, int endIndex, byte value){
        for(int index = startIndex; index < endIndex; index++){
            if(src[index] == value) return index;
        }
        return -1;
    }

    // 在字节数组 src 中，找到每一行请求头部的结尾。由于在 HTTP 请求中，请求头部是以回车符和换行符结尾的
    // 因此当 src[index] == '\n' && src[index - 1] == '\r' 时，index 就是当前请求行的尾部
    public static int findNextLineBreak(byte[] src, int startIndex, int endIndex) {
        for(int index = startIndex; index < endIndex; index++){
            if(src[index] == '\n'){
                if(src[index - 1] == '\r'){
                    return index;
                }
            };
        }
        return -1;
    }

    public static void resolveHttpMethod(byte[] src, int startIndex, HttpHeaders httpHeaders){
        if(matches(src, startIndex, GET)) {
            httpHeaders.httpMethod = HttpHeaders.HTTP_METHOD_GET;
            return;
        }
        if(matches(src, startIndex, POST)){
            httpHeaders.httpMethod = HttpHeaders.HTTP_METHOD_POST;
            return;
        }
        if(matches(src, startIndex, PUT)){
            httpHeaders.httpMethod = HttpHeaders.HTTP_METHOD_PUT;
            return;
        }
        if(matches(src, startIndex, HEAD)){
            httpHeaders.httpMethod = HttpHeaders.HTTP_METHOD_HEAD;
            return;
        }
        if(matches(src, startIndex, DELETE)){
            httpHeaders.httpMethod = HttpHeaders.HTTP_METHOD_DELETE;
            return;
        }
    }

    public static boolean matches(byte[] src, int offset, byte[] value){
        for(int i=offset, n=0; n < value.length; i++, n++){
            if(src[i] != value[n]) return false;
        }
        return true;
    }
}
