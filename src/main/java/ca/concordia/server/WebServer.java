package ca.concordia.server;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

public class WebServer {
    //Allow to quickly change port number if neeed
    private static final int PORT = 5000;
    private static final int MAX_CONCURRENT_THREADS = 50;


    // Ajust size of threadpool dynamically to ensure server can handle all concurrent requests effieciently
    // Threads are created as needed, and idle threads are terminated after 60 seconds
    private final ExecutorService threadPool = Executors.newCachedThreadPool();
    private final Semaphore connectionLimiter = new Semaphore(MAX_CONCURRENT_THREADS); // Limit connections
   
   
   //THREAD-SAFE MECHANISM: CONCURRENT HASH MAP -> Race condition and deadlock prevention
    private final Map<Integer, Account> accounts = new ConcurrentHashMap<>();
    private final Map<Integer, ReentrantLock> locks = new ConcurrentHashMap<>();

    public void start() throws IOException {
        loadAccountsFromFile("src\\main\\resources\\accounts.txt"); // Read accounts from file

        Selector selector = Selector.open();
        ServerSocketChannel serverSocket = ServerSocketChannel.open();
        serverSocket.bind(new InetSocketAddress(PORT));
        serverSocket.configureBlocking(false);
        serverSocket.register(selector, SelectionKey.OP_ACCEPT);

        System.out.println("Server started on port " + PORT);

        while (true) {
            selector.select();
            Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();

            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();

                if (key.isAcceptable()) {
                    handleAccept(selector, key);
                } else if (key.isReadable()) {
                    handleRead(key);
                }
            }
        }
    }

    private void loadAccountsFromFile(String fileName) {
        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                int accountId = Integer.parseInt(parts[0].trim());
                int balance = Integer.parseInt(parts[1].trim());
                accounts.put(accountId, new Account(balance, accountId));
                locks.put(accountId, new ReentrantLock());
            }
        } catch (FileNotFoundException e) {
            System.err.println("Error: File not found: " + fileName);
            System.exit(1); // Exit the program gracefully
        } catch (IOException e) {
            System.err.println("Error reading the file: " + e.getMessage());
        }
    }

    private void handleAccept(Selector selector, SelectionKey key) throws IOException {
        ServerSocketChannel serverSocket = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverSocket.accept();
        clientChannel.configureBlocking(false);
        clientChannel.register(selector, SelectionKey.OP_READ);
        System.out.println("New client connected: " + clientChannel.getRemoteAddress());
    }

    private void handleRead(SelectionKey key) {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(1024);
    
        try {
            //Read client's request
            int bytesRead = clientChannel.read(buffer);
            if (bytesRead == -1) {
                // Client has closed the connection
                clientChannel.close();
                System.out.println("Client disconnected.");
                return;
            }
    
            buffer.flip();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);  // Transfer data from the buffer
            String request = new String(data);
    
            connectionLimiter.acquire(); // Limit number of concurrent threads
            threadPool.submit(() -> {
                try {
                    processRequest(clientChannel, request);
                } finally {
                    connectionLimiter.release();
                }
            });
        } catch (IOException | InterruptedException e) {
            System.err.println("Error reading from client: " + e.getMessage());
            try {
                clientChannel.close();
            } catch (IOException ex) {
                System.err.println("Error closing client connection: " + ex.getMessage());
            }
        } finally {
            buffer.clear(); // Ensure buffer is cleared
        }
    }
    

    private void processRequest(SocketChannel clientChannel, String request) {
        try {
            // Respond to the client
            String response;
            if (request.startsWith("GET")) {
                response = handleGetRequest();
            } else if (request.startsWith("POST")) {
                response = handlePostRequest(request);
            } else {
                response = "HTTP/1.1 400 Bad Request\r\n\r\nInvalid Request.";
            }

            ByteBuffer responseBuffer = ByteBuffer.wrap(response.getBytes());
            clientChannel.write(responseBuffer);
            System.out.println("Response sent to client.");

        } catch (IOException e) {
            System.err.println("Error writing to client: " + e.getMessage());
        }
    }

    private String handleGetRequest() {
        // Restored HTML design from the original code
        return "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n" +
                "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "<title>Concordia Transfers</title>\n" +
                "</head>\n" +
                "<body>\n" +
                "\n" +
                "<h1>Welcome to Concordia Transfers</h1>\n" +
                "<p>Select the account and amount to transfer</p>\n" +
                "\n" +
                "<form action=\"/submit\" method=\"post\">\n" +
                "    <label for=\"account\">Account:</label>\n" +
                "    <input type=\"text\" id=\"account\" name=\"account\"><br><br>\n" +
                "\n" +
                "    <label for=\"value\">Value:</label>\n" +
                "    <input type=\"text\" id=\"value\" name=\"value\"><br><br>\n" +
                "\n" +
                "    <label for=\"toAccount\">To Account:</label>\n" +
                "    <input type=\"text\" id=\"toAccount\" name=\"toAccount\"><br><br>\n" +
                "\n" +
                "    <label for=\"toValue\">To Value:</label>\n" +
                "    <input type=\"text\" id=\"toValue\" name=\"toValue\"><br><br>\n" +
                "\n" +
                "    <input type=\"submit\" value=\"Submit\">\n" +
                "</form>\n" +
                "</body>\n" +
                "</html>";
    }

    private String handlePostRequest(String request) {
        String[] lines = request.split("\r\n");
        String body = lines[lines.length - 1];

        Map<String, String> params = parseFormData(body);
        int fromAccountId, amount, toAccountId;

        try {
            fromAccountId = Integer.parseInt(params.get("account"));
            amount = Integer.parseInt(params.get("value"));
            toAccountId = Integer.parseInt(params.get("toAccount"));
        } catch (NumberFormatException e) {
            return logAndRespondError("Invalid parameters. Ensure accounts and amount are valid numbers.");
        }

        if (amount <= 0) {
            return logAndRespondError("Transaction amount must be greater than 0.");
        }

        ReentrantLock lock1 = locks.get(Math.min(fromAccountId, toAccountId));
        ReentrantLock lock2 = locks.get(Math.max(fromAccountId, toAccountId));

        if (lock1 == null || lock2 == null) {
            return logAndRespondError("Account not found.");
        }

        try {
            if (!lock1.tryLock(2, TimeUnit.SECONDS) || !lock2.tryLock(2, TimeUnit.SECONDS)) {
                return logAndRespondError("Timeout acquiring locks.");
            }

            Account fromAccount = accounts.get(fromAccountId);
            Account toAccount = accounts.get(toAccountId);

            if (fromAccount == null || toAccount == null) {
                return logAndRespondError("One or both accounts do not exist.");
            }

            if (fromAccount.getBalance() < amount) {
                return logAndRespondError("Insufficient balance in account " + fromAccountId);
            }

            // Log transaction details
            System.out.println("Transaction Request: Transfer " + amount + " from Account " + fromAccountId + " to Account " + toAccountId);
            System.out.println("Balance Before Transaction: Account: " + fromAccountId + ", Balance: " + fromAccount.getBalance());
            System.out.println("Balance Before Transaction: Account: " + toAccountId + ", Balance: " + toAccount.getBalance());

            // Perform transaction
            fromAccount.withdraw(amount);
            toAccount.deposit(amount);

            // Log balances after transaction
            System.out.println("Balance After Transaction: Account: " + fromAccountId + ", Balance: " + fromAccount.getBalance());
            System.out.println("Balance After Transaction: Account: " + toAccountId + ", Balance: " + toAccount.getBalance());

            return "HTTP/1.1 200 OK\r\n\r\nTransaction successful.";
        } catch (InterruptedException e) {
            return logAndRespondError("Lock acquisition interrupted.");
        } finally {
            if (lock1.isHeldByCurrentThread()) lock1.unlock();
            if (lock2.isHeldByCurrentThread()) lock2.unlock();
        }
    }

    private String logAndRespondError(String errorMessage) {
        System.err.println("Error: " + errorMessage);
        return "HTTP/1.1 400 Bad Request\r\n\r\n" + errorMessage;
    }

    private Map<String, String> parseFormData(String formData) {
        Map<String, String> params = new HashMap<>();
        String[] pairs = formData.split("&");

        for (String pair : pairs) {
            String[] keyValue = pair.split("=");
            if (keyValue.length == 2) {
                params.put(keyValue[0], keyValue[1]);
            }
        }

        return params;
    }

    public static void main(String[] args) {
        WebServer server = new WebServer();

        try {
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
