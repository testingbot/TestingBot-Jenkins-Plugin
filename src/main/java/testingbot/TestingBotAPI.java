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
        
        details.put("session_id", sessionID);
        details.put("client_key", apiKey);
        details.put("client_secret", apiSecret);

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String,String> entry : details.entrySet())
        {
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(String.format("%s=%s",
                urlEncodeUTF8(entry.getKey().toString()),
                urlEncodeUTF8(entry.getValue().toString())
            ));
        }
        try {
            URL apiUrl = new URL("http://testingbot.com/hq");
            URLConnection conn = apiUrl.openConnection();
            conn.setDoOutput(true);
            
            OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
            wr.write(sb.toString());
            wr.flush();
            
            BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            while ((line = rd.readLine()) != null) {
                System.out.println(line);
            }
    
            wr.close();
            
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
