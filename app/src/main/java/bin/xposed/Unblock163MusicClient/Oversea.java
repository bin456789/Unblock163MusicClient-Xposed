package bin.xposed.Unblock163MusicClient;

import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

import java.net.UnknownHostException;


public class Oversea {
    private static boolean needToCleanDnsCache;
    private static SimpleResolver cnDnsResolver;

    public static void init() throws UnknownHostException {
        cnDnsResolver = new SimpleResolver(Settings.getDnsServer());
    }

    protected static String getIpByHost(String domain) throws TextParseException {
        // caches mechanism built-in, just look it up
        Lookup lookup = new Lookup(domain, Type.A);
        lookup.setResolver(cnDnsResolver);
        if (needToCleanDnsCache) {
            lookup.setCache(null);
            needToCleanDnsCache = false;
        }
        Record[] records = lookup.run();
        if (lookup.getResult() == Lookup.SUCCESSFUL) {
            // already random, just pick index 0
            return records[0].rdataToString();
        }
        return null;
    }

    public static String getIpByHostForUiDnsTest(String domain, String dns) throws TextParseException, UnknownHostException {
        Lookup lookup = new Lookup(domain, Type.A);
        lookup.setResolver(new SimpleResolver(dns));
        lookup.setCache(null);
        Record[] records = lookup.run();
        if (lookup.getResult() == Lookup.SUCCESSFUL)
            return records[0].rdataToString();
        else
            throw new UnknownHostException();
    }

    protected static boolean setDnsServer(String server) {
        try {
            cnDnsResolver = new SimpleResolver(server);
            needToCleanDnsCache = true;
        } catch (UnknownHostException e) {
            return false;
        }
        return true;
    }

}
