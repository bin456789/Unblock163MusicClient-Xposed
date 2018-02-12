package bin.xposed.Unblock163MusicClient;

import android.text.TextUtils;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

@SuppressWarnings("ALL")
class Http {
    private int responseCode;
    private String responseText;
    private long contentLength;
    private String finalLocation;


    private Http(String method, String urlString, String postData, Boolean sendDefaultHead, Map<String, String> additionalHeaders) throws IOException, JSONException, InvocationTargetException, IllegalAccessException {
        int retryCount = 3;
        while (true) {
            try {
                doRequest(method, urlString, postData, sendDefaultHead, additionalHeaders);
                break;
            } catch (Throwable t) {
                t.printStackTrace();
                retryCount--;
                if (retryCount <= 0) {
                    throw t;
                }
            }
        }
    }

    public static Http get(String urlString, Boolean sendDefaultHead) throws IOException, JSONException, InvocationTargetException, IllegalAccessException {
        return new Http("GET", urlString, null, sendDefaultHead, null);
    }

    static Http post(String urlString, Map postData, Boolean sendDefaultHead) throws IOException, JSONException, InvocationTargetException, IllegalAccessException {
        return new Http("POST", urlString, Utility.serialData(postData), sendDefaultHead, null);
    }


    public static Http head(String urlString, Boolean sendDefaultHead) throws IOException, JSONException, InvocationTargetException, IllegalAccessException {
        return new Http("HEAD", urlString, null, sendDefaultHead, null);
    }

    static Http headByGet(String urlString, Boolean sendDefaultHead) throws IOException, JSONException, InvocationTargetException, IllegalAccessException {
        Map<String, String> map = new LinkedHashMap<String, String>() {{
            // 虾米0-0有时会416
            put("Range", "bytes=0-1");
        }};
        return new Http("GET", urlString, null, sendDefaultHead, map);
    }

    private void doRequest(String method, String urlString, String postData, Boolean sendDefaultHead, Map<String, String> additionHeaders) throws IOException, JSONException, InvocationTargetException, IllegalAccessException {
        if (TextUtils.isEmpty(urlString)) {
            return;
        }

        if (TextUtils.isEmpty(method)) {
            method = "GET";
        }

        // url
        urlString = urlString.replace("/thirdyires.imusicapp.cn/res/thirdparty/", "/clientst.musicway.cn/res/");
        URL url = new URL(urlString);
        finalLocation = url.toString();

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setInstanceFollowRedirects(false);

            // method
            method = method.toUpperCase();
            conn.setRequestMethod(method);

            // timeout
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);


            // ua
            conn.setRequestProperty("User-Agent", "Android");

            // header
            if (additionHeaders != null && !additionHeaders.isEmpty()) {
                for (Map.Entry<String, String> entry : additionHeaders.entrySet()) {
                    conn.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }


            // cookie
            if (sendDefaultHead) {
                conn.setRequestProperty("Cookie", CloudMusicPackage.HttpEapi.getDefaultCookie());
                conn.setRequestProperty("Modver", BuildConfig.VERSION_NAME);
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

            // redirect
            responseCode = conn.getResponseCode();
            if (responseCode == 301 || responseCode == 302) {
                conn.disconnect();
                doRequest(method, conn.getHeaderField("Location"), postData, sendDefaultHead, additionHeaders);
                return;
            }

            // whole file size
            if (conn.getHeaderFields().containsKey("Content-Range")) {
                contentLength = Long.parseLong(Utility.getLastPartOfString(conn.getHeaderField("Content-Range"), "/"));
            } else {
                contentLength = conn.getContentLength();
            }


            // receive
            InputStreamReader inputStreamReader;
            StringBuilder sb = new StringBuilder();
            if (responseCode >= 200 && responseCode < 400) {
                inputStreamReader = new InputStreamReader(conn.getInputStream());
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    sb.append(line).append("\n");
                }

                bufferedReader.close();
                inputStreamReader.close();
            }

            responseText = sb.toString();

        } catch (Throwable t) {
            throw t;
        } finally {
            conn.disconnect();
        }

    }

    public int getResponseCode() {
        return responseCode;
    }

    public long getContentLength() {
        return contentLength;
    }

    public String getResponseText() {
        return responseText;
    }

    public String getFinalLocation() {
        return finalLocation;
    }

}

