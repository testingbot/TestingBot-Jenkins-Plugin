package testingbot;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.HttpsURLConnection;
import net.iharder.Base64;
import java.util.ArrayList;
import java.util.List;

import java.io.IOException;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;

import net.sf.json.*;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

/**
 *
 * @author testingbot.com
 */
public class TestingBotAPI {
    
    public void updateTest(String sessionID, Map<String, String> details)
    {
        //always add api credentials
        String apiKey = null;
        String apiSecret = null;
        
        try {
           FileInputStream fstream = new FileInputStream(System.getProperty("user.home") + "/.testingbot");
           // Get the object of DataInputStream
           DataInputStream in = new DataInputStream(fstream);
           BufferedReader br = new BufferedReader(new InputStreamReader(in));
           String strLine = br.readLine();
           String[] data = strLine.split(":");
           apiKey = data[0];
           apiSecret = data[1];
         } catch (Exception e) { return; }
        
        if ((apiKey == null) || (apiSecret == null)) {
            return;
        }

        try {
            DefaultHttpClient httpClient = new DefaultHttpClient();
            String userpass = apiKey + ":" + apiSecret;
            String encoding = Base64.encodeBytes(userpass.getBytes("UTF-8"));

            HttpPut putRequest = new HttpPut("https://api.testingbot.com/v1/tests/" + sessionID);
            putRequest.setHeader("Authorization", "Basic " + encoding);

            List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
            for (Map.Entry<String,String> entry : details.entrySet())
            {
                nameValuePairs.add(new BasicNameValuePair("test[" + entry.getKey().toString() + "]", entry.getValue().toString()));
            }
            
            putRequest.setEntity(new UrlEncodedFormEntity(nameValuePairs));

            HttpResponse response = httpClient.execute(putRequest);
            BufferedReader br = new BufferedReader(
                     new InputStreamReader((response.getEntity().getContent()), "UTF8"));
            String output;
            StringBuilder sb = new StringBuilder();
            while ((output = br.readLine()) != null) {
                    sb.append(output);
            }
            System.out.println(sb.toString());
        } catch (Exception ex) {
            Logger.getLogger(TestingBotAPI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
     private String urlEncodeUTF8(String s) 
     {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new UnsupportedOperationException(e);
        }
    }
}
