package com.skanga;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.sshd.common.io.nio2.Nio2ServiceFactoryFactory;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.config.keys.AuthorizedKeysAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.shell.ProcessShellFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Service;
import org.apache.sshd.server.SshServer;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@SpringBootApplication
public class jsshd
{
    public static void main(String[] args) throws InterruptedException
    {
        SpringApplication.run(SSHServerApp.class, args);
        Thread.currentThread().join();
    }
}

@Service
class SSHServerApp
{
    private final Log sshLogger = LogFactory.getLog(SFTPServerApp.class);
    @Value ("${jssh.host:}")
    private String jsshdHost;
    @Value ("${jssh.port:0}")
    private int jsshdPort;
    @Value ("${jssh.privkey}")
    private String jsshdPrivateKey;
    @Value ("${jssh.authkeys}")
    private String jsshdAuthkeys;
    @Value ("#{${jssh.users}}")
    private Map <String, String> userCreds;

    @PostConstruct
    public void startServer() throws IOException
    {
        start();
    }

    private void start() throws IOException
    {
        SshServer sshd = SshServer.setUpDefaultServer();
        if (!jsshdHost.equals(""))
            sshd.setHost(jsshdHost);
        sshd.setPort(jsshdPort);

        // Creating a host private key which should be stored in a secure location,
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(Paths.get(jsshdPrivateKey)));
        //sshd.setSubsystemFactories(Collections.singletonList(new SftpSubsystemFactory()));
        sshd.setIoServiceFactoryFactory(new Nio2ServiceFactoryFactory());
        sshd.setPasswordAuthenticator(new PasswordAuthenticator()
        {
            @Override
            public boolean authenticate(String userName, String passWord, ServerSession session)
            {
                return userCreds.get(userName).equals(passWord);
            }
        });

        // We can add the public key of the client, usually stored in ~/.ssh/authorized_keys if that file is configured correctly
        Path authKeys = Paths.get(jsshdAuthkeys);
        if (Files.exists(authKeys))
            sshd.setPublickeyAuthenticator(new AuthorizedKeysAuthenticator(authKeys));

        if (Util.isWindows())
            sshd.setShellFactory(new ProcessShellFactory("cmd", "cmd"));
        else
            sshd.setShellFactory(new ProcessShellFactory("/bin/sh", "-i", "-l"));

        sshd.start();
        sshLogger.info("Hostname: " + InetAddress.getLocalHost().getHostName());
        sshLogger.info("Localhost IP address: " + InetAddress.getLocalHost().getHostAddress());
        sshLogger.info("LAN IP address: " + Util.getLocalHostLANAddress().getHostAddress());
        sshLogger.info("SFTP server starting on port: " + jsshdPort);
    }
}

