package com.skanga;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.sshd.common.io.nio2.Nio2ServiceFactoryFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.config.keys.AuthorizedKeysAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

// sftp -oPort=9090 test1@localhost
@SpringBootApplication
public class jsftpd
{
    public static void main(String[] args) throws InterruptedException
    {
        SpringApplication.run(SftpServer.class, args);
        Thread.currentThread().join();
    }
}

@Service
class SftpServer
{
    private final Log sftpLogger = LogFactory.getLog(SftpServer.class);
    @Value ("${jsftpd.host}")
    private String jsftpdHost;
    @Value ("${jsftpd.port}")
    private int jsftpdPort;
    @Value ("${jsftpd.privkey}")
    private String jsftpdPrivateKey;
    @Value ("${jsftpd.authkeys}")
    private String jsftpdAuthkeys;
    @Value ("#{${jsftpd.users}}")
    private Map <String, String> users;

    @PostConstruct
    public void startServer() throws IOException
    {
        start();
    }

    private void start() throws IOException
    {
        SshServer sshd = SshServer.setUpDefaultServer();
        sshd.setHost(jsftpdHost);
        sshd.setPort(jsftpdPort);

        // Creating a host private key which should be stored in a secure location,
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(Paths.get(jsftpdPrivateKey)));
        sshd.setSubsystemFactories(Collections.singletonList(new SftpSubsystemFactory()));
        sshd.setIoServiceFactoryFactory(new Nio2ServiceFactoryFactory());
        sshd.setPasswordAuthenticator(new PasswordAuthenticator()
        {
            @Override
            public boolean authenticate(String username, String password, ServerSession session)
            {
                return users.get(username).equals(password);
            }
        });

        // We can add the public key of the client, usually stored in ~/.ssh/authorized_keys if that file is configured correctly
        Path authKeys = Paths.get(jsftpdAuthkeys);
        if (Files.exists(authKeys))
            sshd.setPublickeyAuthenticator(new AuthorizedKeysAuthenticator(authKeys));
        sshd.start();


        sftpLogger.info("Hostname: " + InetAddress.getLocalHost().getHostName());
        sftpLogger.info("Localhost IP address: " + InetAddress.getLocalHost().getHostAddress());
        sftpLogger.info("LAN IP address: " + getLocalHostLANAddress().getHostAddress());

        sftpLogger.info("SFTP server starting on port: " + jsftpdPort);
    }

    /**
     * Returns an InetAddress object encapsulating what is most likely the machine's LAN IP address.
     * This method will scan all IP addresses on all network interfaces on the host machine to determine
     * the IP address most likely to be the machine's LAN address. If the machine has multiple IP addresses,
     * this method will prefer a site-local IP address (e.g. 192.168.x.x or 10.10.x.x, usually IPv4) if the
     * machine has one (and will return the first site-local address if the machine has more than one), but
     * if the machine does not hold a site-local address, this method will return simply the first non-loopback
     * address found (IPv4 or IPv6).
     */
    private static InetAddress getLocalHostLANAddress() throws UnknownHostException
    {
        try
        {
            InetAddress candidateAddress = null;
            // Iterate all NICs (network interface cards)...
            for (Enumeration <NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces(); networkInterfaces.hasMoreElements(); )
            {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                // Iterate all IP addresses assigned to each card...
                for (Enumeration <InetAddress> inetAddrs = networkInterface.getInetAddresses(); inetAddrs.hasMoreElements(); )
                {
                    InetAddress inetAddr = inetAddrs.nextElement();
                    if (!inetAddr.isLoopbackAddress())
                    {
                        if (inetAddr.isSiteLocalAddress())
                        {
                            // Found non-loopback site-local address. Return it immediately...
                            return inetAddr;
                        }
                        else if (candidateAddress == null)
                        {
                            // Found non-loopback address, but not necessarily site-local.
                            // Store it as a candidate to be returned if site-local address is not subsequently found...
                            candidateAddress = inetAddr;
                            // Note that we don't repeatedly assign non-loopback non-site-local addresses as candidates,
                            // only the first. For subsequent iterations, candidate will be non-null.
                        }
                    }
                }
            }
            if (candidateAddress != null)
            {
                // We did not find a site-local address, but we found some other non-loopback address.
                // Server might have a non-site-local address assigned to its NIC (or it might be running
                // IPv6 which deprecates the "site-local" concept).
                // Return this non-loopback candidate address...
                return candidateAddress;
            }
            // At this point, we did not find a non-loopback address.
            // Fall back to returning whatever InetAddress.getLocalHost() returns...
            InetAddress jdkSuppliedAddress = InetAddress.getLocalHost();
            if (jdkSuppliedAddress == null)
            {
                throw new UnknownHostException("The JDK InetAddress.getLocalHost() method unexpectedly returned null.");
            }
            return jdkSuppliedAddress;
        }
        catch (Exception e)
        {
            UnknownHostException unknownHostException = new UnknownHostException("Failed to determine LAN address: " + e);
            unknownHostException.initCause(e);
            throw unknownHostException;
        }
    }
}
