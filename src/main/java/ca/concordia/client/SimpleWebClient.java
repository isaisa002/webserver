package ca.concordia.client;

import java.io.*;
import java.net.*;

public class SimpleWebClient {

    public static void main(String[] args)  {
        try {
            // Establish a connection to the server
            Socket socket = new Socket("localhost", 5000);

            // Create an output stream to send the request
            OutputStream out = socket.getOutputStream();

            // Create a PrintWriter to write the request
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(out));

            // Prepare the POST request with form data
            String postData = "account=1234&value=1000&toAccount=5678&toValue=500";
            Thread.sleep(60000);
            // Send the POST request
            writer.println("POST /submit HTTP/1.1");
            writer.println("Host: localhost:8080");
            writer.println("Content-Type: application/x-www-form-urlencoded");
            writer.println("Content-Length: " + postData.length());
            writer.println();
            writer.println(postData);
            writer.flush();

            // Create an input stream to read the response
            InputStream in = socket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));

            // Read and print the response
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

            // Close the streams and socket
            reader.close();
            writer.close();
            socket.close();

        } catch (IOException e) {
            e.printStackTrace();
        }catch(InterruptedException e){
            e.printStackTrace();
        }
    }
}
