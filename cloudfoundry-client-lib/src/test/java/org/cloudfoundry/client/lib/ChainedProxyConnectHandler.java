package org.cloudfoundry.client.lib;

import org.eclipse.jetty.server.handler.ConnectHandler;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import javax.net.ssl.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

/**
* Hacky connect handler which is able to open a chained proxy. Usefull when starting an InJvm proxy chained to another
 * corporate proxy.
*/
class ChainedProxyConnectHandler extends ConnectHandler {
    private static final Logger logger = Log.getLogger(ChainedProxyConnectHandler.class);

    private HttpProxyConfiguration httpProxyConfiguration;

    public ChainedProxyConnectHandler(HttpProxyConfiguration httpProxyConfiguration) {
        this.httpProxyConfiguration = httpProxyConfiguration;
    }

    protected SocketChannel connect(HttpServletRequest request, String host, int port) throws IOException
    {
        SocketChannel channel = super.connect(request, httpProxyConfiguration.getProxyHost(), httpProxyConfiguration.getProxyPort());
        Socket socket = channel.socket();

        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();


        establishConnectHandshake(host, port, out, in);
        if (false) {

            // layer SSL on top of an existing socket used to challenge certificates
            // src : http://stackoverflow.com/questions/537040/how-to-connect-to-a-secure-website-using-ssl-in-java-with-a-pkcs12-file
            SSLContext sslContext= null;
            try {
                sslContext = SSLContext.getDefault();
            } catch (NoSuchAlgorithmException e) {
                throw new IOException(e);
            }
            //SSLContext sslContext;
            try {
                sslContext = sslNoCheckCertificate();
            } catch (Exception e) {
                throw new IOException(e);
            }
            SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            SSLSocket sslSocket = null;
            try {
                sslSocket = (SSLSocket) sslSocketFactory.createSocket(socket, socket.getInetAddress().getHostName(), socket.getPort(), true);
            } catch (IOException e) {
                logger.warn("Caught " +e.toString(), e);
                throw e;
            }
            sslSocket.setUseClientMode(true);
            sslSocket.startHandshake();

            return channel;
        } else {
            return channel;
        }
    }

    private SSLContext sslNoCheckCertificate() throws KeyManagementException, NoSuchAlgorithmException {
        // Replaces the certificate checker with a less restrictive one
        TrustManager[] trustAllCerts=new TrustManager[]{
                new X509TrustManager(){
                    public java.security.cert.X509Certificate[] getAcceptedIssuers(){return null;}
                    public void checkClientTrusted(java.security.cert.X509Certificate[] certs,String authType){}
                    public void checkServerTrusted(java.security.cert.X509Certificate[] certs,String authType){}
                }
        };
        SSLContext sslContext= SSLContext.getInstance("SSL");
        sslContext.init(null,trustAllCerts,new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
        return sslContext;
    }

    private void establishConnectHandshake(String host, int port, OutputStream out, InputStream in) throws IOException {
        String connectMessage = "CONNECT "+host+":"+port+" HTTP/1.0\r\n"
                + "Proxy-Connection: Keep-Alive\r\n"
                + "User-Agent: Mozilla/4.0\r\n";

        logger.debug(">>> {}", connectMessage);

        out.write(str2byte(connectMessage));
        out.write(str2byte("\r\n"));
        out.flush();

        int foo=0;

        StringBuffer sb=new StringBuffer();
        while(foo>=0){
            foo=in.read(); if(foo!=13){sb.append((char)foo);  continue;}
            foo=in.read(); if(foo!=10){continue;}
            break;
        }
        if(foo<0){
            throw new IOException();
        }

        String response=sb.toString();
        logger.debug("<<< {}", response);

        String reason="Unknown reason";
        int code=-1;
        try{
            foo=response.indexOf(' ');
            int bar=response.indexOf(' ', foo+1);
            code=Integer.parseInt(response.substring(foo+1, bar));
            reason=response.substring(bar+1);
        }
        catch(Exception e){
        }
        if(code!=200){
            throw new IOException("proxy error: "+reason);
        }

        int count=0;
        while(true){
            count=0;
            while(foo>=0){
                foo=in.read(); if(foo!=13){count++;  continue;}
                foo=in.read(); if(foo!=10){continue;}
                break;
            }
            if(foo<0){
                throw new IOException();
            }
            if(count==0)break;
        }
    }

    byte[] str2byte(String str, String encoding){
        if(str==null)
            return null;
        try{ return str.getBytes(encoding); }
        catch(java.io.UnsupportedEncodingException e){
            return str.getBytes();
        }
    }

    byte[] str2byte(String str){
        return str2byte(str, "UTF-8");
    }
}
