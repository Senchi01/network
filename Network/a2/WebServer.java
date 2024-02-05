import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLConnection;

public class WebServer {
  public static void main(String[] args) {
    try {
      int port = 8080;
      ServerSocket serverSocket = new ServerSocket(port);
      System.out.println("Web server listening on port " + port);

     
      while (true) {
        // Wait for a client connection
        Socket clientSocket = serverSocket.accept();
        Thread thread = new Thread(new ClientHandler(clientSocket, "./public"));

        System.out.println("Client connected from " + clientSocket.getInetAddress().getHostAddress() + ": " + port);

        // Create a new thread to handle the client connection
        thread.start();

      }

    } catch (Exception e) {
      System.err.println("Error: " + e.getMessage());

    }

  }

}

class ClientHandler implements Runnable {
  private Socket clientSocket;
  private String rootDirectory;

  public ClientHandler(Socket clientSocket, String rootDirectory) {
    this.clientSocket = clientSocket;
    this.rootDirectory = rootDirectory;
  }

  public void run() {
    try {

      // Get the input stream of the client socket
      InputStream input = clientSocket.getInputStream();

      // Read the request message sent by the client from the input stream
      BufferedReader reader = new BufferedReader(new InputStreamReader(input));
      String requestMessage = reader.readLine();
      System.out.println("Received request message: " + requestMessage);

      // Parse the request message to extract the requested file path and HTTP method
      String[] requestTokens = requestMessage.split(" ");
      String httpMethod = requestTokens[0];
      String filePath = requestTokens[1];
      if (filePath.equals("/")) {
        filePath = "/index.html"; // Default file to serve

      }

      
      filePath = rootDirectory + filePath;
      File requestedFile = new File(filePath);
      if (requestedFile.isDirectory() && filePath.matches(".*/[a-z]+$*/")) {
        filePath += "/index.html"; // Serve index.html in the directory
      } else if (requestedFile.isDirectory() && filePath.matches(".*/[a-z]+$")) {
        filePath += "/index.html"; // Serve index.html in the directory
      }
      System.out.println("HTTP method: " + httpMethod);
      System.out.println("Requested file path: " + filePath);

      String[] pathTokens = filePath.split("\\.");
      String extension = pathTokens[pathTokens.length - 1];
      String mimeType = URLConnection.guessContentTypeFromName(filePath);

      // Open an input stream for the requested file
      File file = new File(filePath);

      if (!file.exists() || !file.canRead()) {
        // Return a 404 Not Found response if the file does not exist or cannot be read
        String responseMessage = "HTTP/1.1 404 Not Found\r\n\r\n";
        OutputStream output = clientSocket.getOutputStream();
        output.write(responseMessage.getBytes());
        output.flush();
        clientSocket.close();
        String s = "response: " + responseMessage;
        System.out.println(s);
        return;
      }
      
      InputStream fileInput = new FileInputStream(file);

      // Construct the response message with the appropriate HTTP headers and file
      // content
      if (extension.equalsIgnoreCase("html")) {
        BufferedReader fileReader = new BufferedReader(new FileReader(filePath));
        StringBuilder fileContents = new StringBuilder();
        String line;
        while ((line = fileReader.readLine()) != null) {
          fileContents.append(line);
        }  if (httpMethod.equals("GET") && filePath.equals("/redirect")) {
          // Redirect the client to a different URL if the request is for /redirect
          String redirectUrl = "http://www.google.com";
          String responseMessage = "HTTP/1.1 302 Found\r\nLocation: " + redirectUrl + "\r\n\r\n";
          OutputStream output = clientSocket.getOutputStream();
          output.write(responseMessage.getBytes());
          output.flush();
          String s = "response: " + responseMessage;
          System.out.println(s);
      } else {
        String responseMessage = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n" + fileContents.toString();
        OutputStream output = clientSocket.getOutputStream();
        String[] t = responseMessage.split("\n");
        System.out.println( "response: " + t[0] + "\n");
        output.write(responseMessage.getBytes());
        output.flush();
        
      }
      fileReader.close();
      } else {
        String responseMessage = "HTTP/1.1 200 OK\r\n" +
            "Content-Type: " + mimeType + "\r\n" +
            "Content-Length: " + file.length() + "\r\n\r\n";
        OutputStream output = clientSocket.getOutputStream();
        output.write(responseMessage.getBytes());

        // Write the file content to the output stream
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = fileInput.read(buffer)) != -1) {
          output.write(buffer, 0, bytesRead);
        }
        output.flush();
        output.close();
        String s = "response: " + responseMessage;
        System.out.println(s);

      }

      // Close the client socket when done
      // Close the input stream, output stream, and client socket
      input.close();
      fileInput.close();
      clientSocket.close();



    } catch (Exception e) {
      
      System.err.println("Error handling client request: " + e.getMessage());
       // Return a 500 Internal Server Error response
       String responseMessage = "HTTP/1.1 500 Internal Server Error\r\n\r\n";
       OutputStream output;
      try {
        output = clientSocket.getOutputStream();
        output.write(responseMessage.getBytes());
        output.flush();
        clientSocket.close();
        String s = "response: " + responseMessage;
        System.out.println(s);
        return;
      } catch (IOException e1) {
        e1.printStackTrace();
      }
       
    } 

  }
}
