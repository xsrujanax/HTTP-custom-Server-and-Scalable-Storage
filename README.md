# HTTP File Server and Reliable UDP Communication

This project implements an HTTP file server with reliable data transfer using UDP. The system supports HTTP GET and POST requests and features a custom implementation of the Selective Repeat protocol for reliable communication.

## Features
- **HTTP GET and POST Support**: Handles standard HTTP requests for fetching and saving data.
- **File Storage and Retrieval**: Supports file storage and retrieval with directory browsing capabilities.
- **Reliable UDP Communication**: Implements the Selective Repeat protocol to ensure reliable data transfer over UDP.
- **Three-Way Handshake**: Establishes connections using a custom three-way handshake process.
- **Custom Packet Implementation**: Encapsulates network packets with custom types and sequence numbers.
- **Verbose Logging**: Provides detailed logs of client-server communication for debugging purposes.

## File Structure
- `httpc.java`: Command-line tool for HTTP GET and POST requests.
- `HttpClient.java`: Handles HTTP requests and responses for the client.
- `HTTPFileStorage.java`: Core server logic for handling HTTP requests.
- `https.java`: Main entry point for the server application.
- `Messages.java`: Utility class for displaying help messages.
- `Packet.java`: Custom network packet implementation.
- `ReliableSRReceiver.java`: Selective Repeat receiver implementation.
- `ReliableSRSender.java`: Selective Repeat sender implementation.
- `UDPClient.java`: Client-side UDP communication logic.
- `UDPServer.java`: Server-side UDP communication logic.

## Prerequisites
1. Java 11 or higher installed on your system.
2. Basic understanding of HTTP and UDP protocols.

## Instructions to Execute

### Server Setup
1. **Compile the Server Code**:
   - Open a terminal/command prompt.
   - Navigate to the directory containing `HTTPFileStorage.java`, `https.java`, and related files.
   - Compile the server code:
     ```
     javac *.java
     ```

2. **Start the Server**:
   - Run the server with the following command:
     ```
     java https [-v] [-p port] [-d directory]
     ```
     - `-v`: Enables verbose logging.
     - `-p`: Specifies the port number (default: 8080).
     - `-d`: Specifies the base directory for file storage.

   Example:
   ```
   java https -v -p 8080 -d /path/to/storage
   ```

### Client Usage
1. **Compile the Client Code**:
   - Open a terminal/command prompt.
   - Navigate to the directory containing `httpc.java` and related files.
   - Compile the client code:
     ```
     javac *.java
     ```

2. **Run the Client**:
   - Use the client to perform HTTP GET and POST requests:
     - **GET Request**:
       ```
       java httpc get [-v] [-h key:value] URL
       ```
       - `-v`: Enables verbose logging.
       - `-h`: Specifies custom headers.

       Example:
       ```
       java httpc get -v -h "Content-Type:application/json" http://localhost:8080/file
       ```

     - **POST Request**:
       ```
       java httpc post [-v] [-h key:value] [-d inline-data] [-f file] URL
       ```
       - `-v`: Enables verbose logging.
       - `-h`: Specifies custom headers.
       - `-d`: Inline data for the request body.
       - `-f`: File to be sent in the request body.

       Example:
       ```
       java httpc post -v -h "Content-Type:application/json" -d "{\"key\":\"value\"}" http://localhost:8080/file
       ```

### Testing Reliable UDP Communication
1. **Run the UDP Server**:
   - Ensure the server is running as described in the "Server Setup" section.

2. **Run the UDP Client**:
   - Send a request to the server:
     ```
     java httpc get -v http://localhost:8080/sample
     ```
   - The Selective Repeat protocol ensures reliable communication.

## Example Output
```plaintext
Server is listening on port 8080
Data Directory: /path/to/storage
Request received: GET /sample
Host: localhost
...
Response sent: HTTP/1.0 200 OK
Content-type: application/json
...
```

## Notes
- The system uses custom packet structures to ensure reliable data transfer over UDP.
- The server handles file operations securely, preventing access to unauthorized directories.
- Verbose mode is recommended for debugging and understanding the flow of requests and responses.

## License
This project is for educational purposes and demonstrates concepts in HTTP and UDP communication.

