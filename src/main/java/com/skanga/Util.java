package com.skanga;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.Locale;

public class Util
{
    private static final boolean AIX;
    private static final boolean LINUX;
    private static final boolean MAC;
    private static final boolean WINDOWS;
    private static final boolean VM_IBM_JDK;
    private static final boolean VM_OPEN_JDK;
    private static final String JAVA;
    private static final Path JAVA_HOME;

    static
    {
        final String os = getOSName();
        AIX = os.equals("aix");
        LINUX = os.equals("linux");
        MAC = os.startsWith("mac");
        WINDOWS = os.contains("win");

        VM_IBM_JDK = System.getProperty("java.vm.name").startsWith("IBM");
        VM_OPEN_JDK = System.getProperty("java.vm.name").startsWith("OpenJDK");

        String javaExecutable = "java";
        if (WINDOWS)
        {
            javaExecutable = "java.exe";
        }
        JAVA = javaExecutable;

        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome == null)
        {
            javaHome = System.getProperty("java.home");
        }
        JAVA_HOME = Paths.get(javaHome);
    }

    public static String getOSName()
    {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT);
    }

    public static boolean isAIX()
    {
        return AIX;
    }

    public static boolean isLinux()
    {
        return LINUX;
    }

    public static boolean isMac()
    {
        return MAC;
    }

    public static boolean isWindows()
    {
        return WINDOWS;
    }

    public static boolean isUnknown()
    {
        return !AIX && !LINUX && !MAC && !WINDOWS;
    }

    public static boolean isIbmJDK()
    {
        return VM_IBM_JDK;
    }

    public static boolean isOpenJDK()
    {
        return VM_OPEN_JDK;
    }

    public static Path getJavaExecutablePath()
    {
        return Paths.get(JAVA_HOME.toString(), "bin", JAVA);
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
    static InetAddress getLocalHostLANAddress() throws UnknownHostException
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
