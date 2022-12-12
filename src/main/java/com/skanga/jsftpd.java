package com.skanga;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.sshd.common.io.nio2.Nio2ServiceFactoryFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.config.keys.AuthorizedKeysAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.sftp.server.FileHandle;
import org.apache.sshd.sftp.server.Handle;
import org.apache.sshd.sftp.server.SftpEventListener;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

// sftp -oPort=9090 test1@localhost
@SpringBootApplication
public class jsftpd
{
    public static void main(String[] args) throws InterruptedException
    {
        SpringApplication.run(SFTPServerApp.class, args);
        Thread.currentThread().join();
    }
}

@Service
class SFTPServerApp
{
    private final Log sftpLogger = LogFactory.getLog(SFTPServerApp.class);
    @Value ("${jssh.host:}")
    private String jsftpdHost;
    @Value ("${jssh.port:0}")
    private int jsftpdPort;
    @Value ("${jssh.privkey}")
    private String jsftpdPrivateKey;
    @Value ("${jssh.authkeys}")
    private String jsftpdAuthkeys;
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
        if (!jsftpdHost.equals(""))
            sshd.setHost(jsftpdHost);
        sshd.setPort(jsftpdPort);

        // Creating a host private key which should be stored in a secure location,
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(Paths.get(jsftpdPrivateKey)));
        sshd.setIoServiceFactoryFactory(new Nio2ServiceFactoryFactory());
        sshd.setPasswordAuthenticator(new PasswordAuthenticator()
        {
            @Override
            public boolean authenticate(String userName, String passWord, ServerSession session)
            {
                return userCreds.get(userName).equals(passWord);
            }
        });

        SftpSubsystemFactory sftp = new SftpSubsystemFactory();
        sftp.addSftpEventListener(new SftpEventListener()
        {
            private final Map<ServerSession, String> writtenFilePath = new HashMap <>();
            private final Map<ServerSession, String> readFilePath = new HashMap <>();

            @Override
            public void read(ServerSession session, String remoteHandle, FileHandle localHandle, long offset, byte[] data, int dataOffset, int dataLen, int readLen, Throwable thrown) throws IOException
            {
                SftpEventListener.super.read(session, remoteHandle, localHandle, offset, data, dataOffset, dataLen, readLen, thrown);
                readFilePath.put(session, localHandle.getFile().toString());
            }

            @Override
            public void written(ServerSession session, String remoteHandle, FileHandle localHandle, long offset,
                                byte[] data, int dataOffset, int dataLen, Throwable thrown) throws IOException
            {
                SftpEventListener.super.written(session, remoteHandle, localHandle, offset, data, dataOffset, dataLen, thrown);
                writtenFilePath.put(session, localHandle.getFile().toString());
            }

            @Override
            public void closed(ServerSession session, String remoteHandle, Handle localHandle, Throwable thrown)
            {
                if (writtenFilePath.containsKey(session))
                    sftpLogger.info("[SFTP_EVENT] session:" + session + " written file: " + writtenFilePath.remove(session));
                else if (readFilePath.containsKey(session))
                    sftpLogger.info("[SFTP_EVENT] session:" + session + " read file: " + readFilePath.remove(session));
            }

            @Override
            public void removed(ServerSession session, Path path, boolean isDirectory, Throwable thrown) throws IOException
            {
                SftpEventListener.super.removed(session, path, isDirectory, thrown);
                sftpLogger.info("[SFTP_EVENT] session:" + session + " removed " + (isDirectory ? "directory" : "file") + ": " + path);
            }

            @Override
            public void created(ServerSession session, Path path, Map<String, ?> attrs, Throwable thrown) throws IOException
            {
                SftpEventListener.super.created(session, path, attrs, thrown);
                sftpLogger.info("[SFTP_EVENT] session:" + session + " created directory: " + path);
            }

            @Override
            public void moved(ServerSession session, Path srcPath, Path dstPath, Collection <CopyOption> opts, Throwable thrown) throws IOException
            {
                SftpEventListener.super.moved(session, srcPath, dstPath, opts, thrown);
                sftpLogger.info("[SFTP_EVENT] session:" + session + " moved path src:" + srcPath + " dst:" + dstPath);
            }
        });
        sshd.setSubsystemFactories(Collections.singletonList(sftp));

        // We can add the public key of the client, usually stored in ~/.ssh/authorized_keys if that file is configured correctly
        Path authKeys = Paths.get(jsftpdAuthkeys);
        if (Files.exists(authKeys))
            sshd.setPublickeyAuthenticator(new AuthorizedKeysAuthenticator(authKeys));
        sshd.start();

        sftpLogger.info("Hostname: " + InetAddress.getLocalHost().getHostName());
        sftpLogger.info("Localhost IP address: " + InetAddress.getLocalHost().getHostAddress());
        sftpLogger.info("LAN IP address: " + Util.getLocalHostLANAddress().getHostAddress());
        sftpLogger.info("SFTP server starting on port: " + jsftpdPort);
    }
}
