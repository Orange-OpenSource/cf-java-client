package org.cloudfoundry.client.lib;

import java.io.IOException;
import java.net.*;

/**
 * Byteman helper which rejects Sockets on non jetty threads that do not target the local http proxy.
 * Calls to this class are dynamically injected into JDK java.net.Socket bytecode by byteman.
 */
public class SocketDestHelper {

    private static final ThreadLocal<Boolean> isSocketRestrictingOnlyLocalHost = new ThreadLocal<Boolean>();

    public  void setForbiddenOnCurrentThread() {
        setForbiddenOnCurrentThread(true);
    }

    public void setForbiddenOnCurrentThread(boolean skipInjvmProxy) {
        isSocketRestrictingOnlyLocalHost.set(skipInjvmProxy);
    }


    public void throwExceptionIfForbidden(String host, int port) throws IOException {
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
            throwExceptionIfForbidden(inetAddress.getHostName(), inetAddress.getPort());
        }
    }

}
