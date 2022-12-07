package com.skanga;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
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
    @Value("#{${jsftpd.users}}")
    private Map <String,String> users;

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
        sftpLogger.info("SFTP server starting on port: " + jsftpdPort);
    }
}
