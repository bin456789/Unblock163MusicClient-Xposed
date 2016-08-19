package bin.xposed.Unblock163MusicClient;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class Http {

    public int responseCode;
    public String responseText;
    public int contentLength;

    private Http(String method, String urlString, String postData, Boolean sendDefaultHead, Map<String, String> additionalHeaders) throws IOException, JSONException {
        int retryCount = 2;
        while (true) {
            try {
                doRequest(method, urlString, postData, sendDefaultHead, additionalHeaders);
                break;
            } catch (Exception e) {
                e.printStackTrace();
                retryCount--;
                if (retryCount <= 0)
                    throw e;
            }
        }
    }

    public static String get(String urlString, Boolean sendDefaultHead) throws IOException, JSONException {
        return new Http("GET", urlString, null, sendDefaultHead, null).responseText;
    }

    public static String post(String urlString, String postData, Boolean sendDefaultHead) throws IOException, JSONException {
        return new Http("POST", urlString, postData, sendDefaultHead, null).responseText;
    }

    public static Http head(String urlString, Boolean sendDefaultHead) throws IOException, JSONException {
        return new Http("HEAD", urlString, null, sendDefaultHead, null);
    }

    public static Http headByGet(String urlString, Boolean sendDefaultHead) throws IOException, JSONException {
        Map<String, String> map = new LinkedHashMap<String, String>() {{
            put("Range", "bytes=0-0");
        }};
        return new Http("GET", urlString, null, sendDefaultHead, map);
    }

    private void doRequest(String method, String urlString, String postData, Boolean sendDefaultHead, Map<String, String> additionHeaders) throws IOException, JSONException {
        if (method == null || urlString == null)
            return;

        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        method = method.toUpperCase();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(5000);
        CookieHandler.setDefault(new CookieManager()); // avoid two cookie header, __cfduid
        if (sendDefaultHead) {
            conn.setRequestProperty("User-Agent", "android");
            conn.setRequestProperty("Cookie", CloundMusicPackage.HttpEapi.getDefaultCookie());
        }

        if (additionHeaders != null)
            for (Map.Entry<String, String> entry : additionHeaders.entrySet()) {
                conn.setRequestProperty(entry.getKey(), entry.getValue());
            }


        if ("HEAD".equals(method)) {
            conn.setRequestProperty("Accept-Encoding", "identity");
        }


        // send post data
        if ("POST".equals(method)) {
            conn.setDoOutput(true);

            OutputStream outputStream = conn.getOutputStream();
            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream));
            bufferedWriter.write(postData);
            bufferedWriter.close();
            outputStream.close();
        }

        // receive
        responseCode = conn.getResponseCode();

        // whole file size
        if (conn.getHeaderFields().containsKey("Content-Range")) {
            contentLength = Integer.parseInt(Utility.getLastPartOfString(conn.getHeaderField("Content-Range"), "/"));
        } else
            contentLength = conn.getContentLength();


        InputStreamReader inputStreamReader;
        StringBuilder sb = new StringBuilder();
        if (responseCode >= 200 && responseCode < 400) {
            if ("gzip".equals(conn.getContentEncoding()))
                inputStreamReader = new InputStreamReader(new GZIPInputStream(conn.getInputStream()));
            else
                inputStreamReader = new InputStreamReader(conn.getInputStream());
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

            String line;
            while ((line = bufferedReader.readLine()) != null)
                sb.append(line).append("\n");

            bufferedReader.close();
            inputStreamReader.close();
        }
        conn.disconnect();
        responseText = sb.toString();
    }
}

