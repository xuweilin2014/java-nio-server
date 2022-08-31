package com.jenkov.nioserver.http;

import org.junit.Assert;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * Created by jjenkov on 19-10-2015.
 */
public class HttpUtilTest {

    @Test
    public void testResolveHttpMethod() throws UnsupportedEncodingException {
        assertHttpMethod("GET / HTTP/1.1\r\n" , HttpHeaders.HTTP_METHOD_GET);
        assertHttpMethod("POST / HTTP/1.1\r\n", HttpHeaders.HTTP_METHOD_POST);
        assertHttpMethod("PUT / HTTP/1.1\r\n", HttpHeaders.HTTP_METHOD_PUT);
        assertHttpMethod("HEAD / HTTP/1.1\r\n", HttpHeaders.HTTP_METHOD_HEAD);
        assertHttpMethod("DELETE / HTTP/1.1\r\n", HttpHeaders.HTTP_METHOD_DELETE);
    }

    private void assertHttpMethod(String httpRequest, int httpMethod) throws UnsupportedEncodingException {
        byte[] source = httpRequest.getBytes("UTF-8");
        HttpHeaders httpHeaders = new HttpHeaders();

        HttpUtil.resolveHttpMethod(source, 0, httpHeaders);
        Assert.assertEquals(httpMethod, httpHeaders.httpMethod);
    }

    @Test
    public void testParseHttpRequest() throws UnsupportedEncodingException {
        String httpRequest = "GET / HTTP/1.1\r\n\r\n";

        byte[] source = httpRequest.getBytes("UTF-8");
        HttpHeaders httpHeaders = new HttpHeaders();

        HttpUtil.parseHttpRequest(source, 0, source.length, httpHeaders);

        Assert.assertEquals(0, httpHeaders.contentLength);

        httpRequest =
                "GET / HTTP/1.1\r\n" +
                "Content-Length: 5\r\n" +
                "\r\n1234";
        source = httpRequest.getBytes("UTF-8");

        Assert.assertEquals(-1, HttpUtil.parseHttpRequest(source, 0, source.length, httpHeaders));
        Assert.assertEquals(5, httpHeaders.contentLength);


        httpRequest =
                "GET / HTTP/1.1\r\n" +
                "Content-Length: 5\r\n" +
                "\r\n12345";
        source = httpRequest.getBytes("UTF-8");

        Assert.assertEquals(42, HttpUtil.parseHttpRequest(source, 0, source.length, httpHeaders));
        Assert.assertEquals(5, httpHeaders.contentLength);


        httpRequest =
                "GET / HTTP/1.1\r\n" +
                "Content-Length: 5\r\n" +
                "\r\n12345" +
                "GET / HTTP/1.1\r\n" +
                "Content-Length: 5\r\n" +
                "\r\n12345";

        source = httpRequest.getBytes("UTF-8");

        Assert.assertEquals(42, HttpUtil.parseHttpRequest(source, 0, source.length, httpHeaders));
        Assert.assertEquals(5, httpHeaders.contentLength);
        Assert.assertEquals(37, httpHeaders.bodyStartIndex);
        Assert.assertEquals(42, httpHeaders.bodyEndIndex);

        httpRequest =
                "GET / HTTP/1.1\r\n" +
                "User-Agent: curl/7.16.3 libcurl/7.16.3 OpenSSL/0.9.7l zlib/1.2.3\r\n" +
                "Host: www.example.com\r\n" +
                "Accept-Language: en, mi\r\n" +
                "Content-Length: 6\r\n" +
                "\r\n123456";
        source = httpRequest.getBytes(StandardCharsets.UTF_8);
        HttpUtil.parseHttpRequest(source, 0, source.length, httpHeaders);
        Assert.assertEquals(6, httpHeaders.contentLength);

    }



}
