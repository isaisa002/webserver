package ca.concordia.client;

import java.io.*;
import java.net.*;

public class SimpleWebClientDeadlock implements Runnable {

    private int fromAccount;
    private int toAccount;

    public SimpleWebClientDeadlock(int fromAccount, int toAccount) {
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
    }

    public void run() {
        Socket socket = null;
        PrintWriter writer = null;
        BufferedReader reader = null;

        try {
            // Establish a connection to the server
            socket = new Socket("localhost", 5000);
            System.out.println("Connected to server");

            // Create an output stream to send the request and a PrintWriter to write the request
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));

            // Prepare the POST request with form data
            String postData = "account=" + fromAccount + "&value=1&toAccount=" + toAccount + "&toValue=1";
            //create a random number between 1000 and 60000
            //int waitfor = (int)(Math.random() * 1000 + 200);
            //Thread.sleep(waitfor);
            // Send the POST request
            writer.println("POST /submit HTTP/1.1");
            writer.println("Host: localhost:5000");
            writer.println("Content-Type: application/x-www-form-urlencoded");
            writer.println("Content-Length: " + postData.length());
            writer.println();
            writer.print(postData); 
            writer.flush();

            // Create an input stream to read the response
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Read and print the response
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException e) {
            System.err.println("Error during communication with server: " + e.getMessage());
        } finally {
            try {

                //Avoid null pointer exception by checking for null before closing resources
                if (reader != null) reader.close();
                if (writer != null) writer.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
                System.err.println("Error closing resources: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        // Create multiple clients to test the server
        for (int i = 0; i < 500; i++) {
            System.out.println("Creating client " + i);
            Thread thread1 = new Thread(new SimpleWebClientDeadlock(123, 345));
            Thread thread2 = new Thread(new SimpleWebClientDeadlock(345, 123));
            thread1.start();
            thread2.start();
        }
    }
}
