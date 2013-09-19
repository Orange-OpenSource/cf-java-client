package org.cloudfoundry.client.lib;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Byteman helper which rejects Sockets on non jetty threads that do not target the local http proxy.
 * Calls to this class are dynamically injected into JDK java.net.Socket bytecode by byteman.
 */
public class SocketDestHelper {

    private static final ThreadLocal<Boolean> isSocketRestrictingOnlyLocalHost = new ThreadLocal<Boolean>();

    private static final Set<String> installedRules = Collections.synchronizedSet(new HashSet<String>());
    private static final AtomicBoolean isActivated = new AtomicBoolean(false);
    //Byteman API

    public static void activated() {
        System.out.println("SocketDestHelper activated");
        System.out.flush();
        isActivated.set(true);
    }
    public static void installed(String ruleName) {
        System.out.println("SocketDestHelper installed:" + ruleName);
        System.out.flush();
        installedRules.add(ruleName);

    }
    public static void uninstalled(String ruleName) {
        System.out.println("SocketDestHelper uninstalled:" + ruleName);
        System.out.flush();
        installedRules.remove(ruleName);
    }

    public static void deactivated() {
        System.out.println("SocketDestHelper deactivated");
        System.out.flush();
        isActivated.set(false);
    }

    public static Set<String> getInstalledRules() {
        return installedRules;
    }

    public static boolean isActivated() {
        return isActivated.get();
    }

    public static boolean isSocketRestrictionFlagActive() {
        return isSocketRestrictingOnlyLocalHost.get();
    }

    public  void setForbiddenOnCurrentThread() {
        isSocketRestrictingOnlyLocalHost.set(true);
    }

    public void alwaysThrowException() throws IOException {
        IOException ioe = new IOException("always throws IOE");
        ioe.printStackTrace();
        throw ioe;
    }


    public void throwExceptionIfForbidden(String host, int port) throws IOException {
        System.out.println("throwExceptionIfForbidden(host=" + host + " port=" + port + ") with isSocketRestrictingOnlyLocalHost=" + isSocketRestrictingOnlyLocalHost.get());
        Boolean flag = isSocketRestrictingOnlyLocalHost.get();
        if (flag != null && flag.booleanValue()) {
            if (! host.equals("127.0.0.1") && ! host.equals("localhost")) {
                IOException ioe = new IOException("detected direct socket connect while tests expect them to go through proxy instead: Only jetty proxy threads should go through external hosts, got:host=" + host + " port=" + port);
                ioe.printStackTrace();
                throw ioe;
            }
        }
    }
    public void throwExceptionIfForbidden(SocketAddress address) throws IOException {
        if (address instanceof InetSocketAddress) {
            InetSocketAddress inetAddress = (InetSocketAddress) address;
            throwExceptionIfForbidden(inetAddress);
        }
    }

    public void throwExceptionIfForbidden(InetSocketAddress inetAddress) throws IOException {
        throwExceptionIfForbidden(inetAddress.getHostName(), inetAddress.getPort());
    }
    public void throwExceptionIfForbidden(InetSocketAddress inetAddress, int port) throws IOException {
        throwExceptionIfForbidden(inetAddress.getHostName(), port);
    }


    public SocketFactory getDefaultSslSocketFactory() {
        System.out.println("SocketDestHelper.getDefaultSslSocketFactory()");
        System.out.flush();
        return new SocketFactoryInterceptor();
    }

    /**
     * Wraps default SSLSocketFactory to reject SSL sockets opened directly from non-jetty threads
     */
    public static class SocketFactoryInterceptor extends SocketFactory {

        private SocketFactory impl;

        SocketFactoryInterceptor() {
            this.impl = SSLSocketFactory.getDefault();
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
            new SocketDestHelper().throwExceptionIfForbidden(host, port);
            return impl.createSocket(host, port);
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException, UnknownHostException {
            new SocketDestHelper().throwExceptionIfForbidden(host, port);
            return impl.createSocket(host, port, localHost, localPort);
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            new SocketDestHelper().throwExceptionIfForbidden(host.getHostName(), port);
            return impl.createSocket(host, port);
        }

        @Override
        public Socket createSocket(InetAddress host, int port, InetAddress localHost, int localPort) throws IOException {
            return impl.createSocket(host, port, localHost, localPort);
        }

    }


}
