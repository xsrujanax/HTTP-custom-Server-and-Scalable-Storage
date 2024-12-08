import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.*;

public class HttpClient {
    public static String httpGET(String url, int port, boolean verbose, String requestHeaders) throws IOException, URISyntaxException {
        URI uri = new URI(url);
        String host = uri.getHost();
        String path = uri.getRawPath();
        String queryParameters = uri.getRawQuery();
        UDPClient myClientSocket = new UDPClient(3000, port,verbose);
        String[] headers = requestHeaders.split(",");

        // Construct the request message
        StringBuilder requestMessage = new StringBuilder(String.format("GET %s?%s HTTP/1.0\r\nHost: %s\r\nUser-Agent: Concordia-HTTP/1.0\r\n", path, queryParameters, host));
        for (String header : headers) {
            requestMessage.append(header).append("\r\n");
        }
        requestMessage.append("\r\n"); // Empty line to indicate the end of headers
        myClientSocket.send(requestMessage.toString());

        String output = UDPClient.receive();
        return processResponse(verbose, output);

    }


    public static String httpPOST(String URL, String parameters, String requestHeaders, int port, boolean verbose) throws IOException, URISyntaxException {
        URI uri = new URI(URL);
        String host = uri.getHost();
        String path = uri.getRawPath();

        String[] headers = requestHeaders.split(",");

        // Construct the request message
        String requestMessage = String.format("POST %s HTTP/1.0\r\nHost: %s\r\n", path, host);
        for (String header : headers) {
            requestMessage += header + "\r\n";
        }
        requestMessage += "Content-Length: " + parameters.length() + "\r\n";
        requestMessage += "User-Agent: Concordia-HTTP/1.0\r\n\r\n";
        requestMessage += parameters;
        UDPClient myClientSocket = new UDPClient(3000, port, verbose);
        // Send the request message
        myClientSocket.send(requestMessage);

        // Receive the response
        String output = UDPClient.receive();
        return processResponse(verbose, output);
    }

    private static String processResponse(boolean verbose, String output) throws IOException {
        StringReader stringReader = new StringReader(output);
        BufferedReader in = new BufferedReader(stringReader);
        String responseLine = in.readLine();
        String line;

        StringBuilder responseHeaders = new StringBuilder(responseLine).append("\n");
        while (!(line = in.readLine()).isEmpty()) {
            responseHeaders.append(line).append("\n");
        }
        StringBuilder responseBody = new StringBuilder();
        while ((line = in.readLine()) != null) {
            responseBody.append(line).append("\n");
        }
        if (verbose) {
            return responseHeaders.append(responseBody).toString();
        } else {
            return responseBody.toString();
        }
    }
}

