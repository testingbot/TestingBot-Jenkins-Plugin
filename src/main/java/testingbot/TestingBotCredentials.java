package testingbot;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TestingBotCredentials {
    private static TestingBotCredential cachedCredentials = null;
    
    public static TestingBotCredential getCredentials() {
        if (cachedCredentials != null) {
            return cachedCredentials;
        }
        
        //always add api credentials
        String apiKey = null;
        String apiSecret = null;
        
        try {
           FileInputStream fstream = new FileInputStream(Paths.get(System.getProperty("user.home"), ".testingbot").toFile());
           // Get the object of DataInputStream
           DataInputStream in = new DataInputStream(fstream);
           BufferedReader br = new BufferedReader(new InputStreamReader(in));
           String strLine = br.readLine();
           String[] data = strLine.split(":");
           apiKey = data[0];
           apiSecret = data[1];
           
           cachedCredentials = new TestingBotCredential(apiKey, apiSecret);
           return cachedCredentials;
        } catch (IOException e) {
            Logger.getLogger(TestingBotCredentials.class.getName()).log(Level.SEVERE, null, e);
        }
        
        return null; 
    }
}
