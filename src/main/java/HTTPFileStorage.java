// ..........................................
// Assignment # 2!
// © @Srujana Guttula - 40237663 , @Aakansha -  40188693
//
// ..........................................
import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

public class HTTPFileStorage {
    private static String host;
    private String requestLine;
    private final String baseDirectory;
    private final int port;
    private final boolean verbose;
    private static int statusCode = 200;
    private boolean overWrite=true;

    Map<Integer,String> map = new HashMap<>();

    public HTTPFileStorage(int port, String baseDirectory, boolean verbose) {
        this.port = port;
        this. baseDirectory = baseDirectory;
        this.verbose = verbose;

    }

    public String getBaseDirectory() {
        return baseDirectory;
    }

    static void setHost(String host){
        HTTPFileStorage.host = host;
    }

    static String getHost(){
        return host;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        HTTPFileStorage.statusCode = statusCode;
    }

    public void startServer() throws IOException {
        map.put(200,"OK");
        map.put(201,"Created");
        map.put(404,"File Not Found");
        map.put(403,"Permission Denied");
        map.put(500, "Internal Server Error");

        //set up the server and accept the request
        UDPServer myServerSocket = new UDPServer(port,verbose);
        System.out.println("Server is listening to port" + port);
        System.out.println("Data Directory:" + baseDirectory);

        while (true) {
            if(UDPServer.acceptConnectionRequest()!=0){
            //Receive the request from the client and process it
            BufferedReader reader = new BufferedReader(new StringReader(UDPServer.receive()));
            String response = processRequest(reader);

            if (verbose) {
                String host = getHost();
                if (host == null)
                    host = "localhost";
                System.out.println("\nRequest received:");
                System.out.println(requestLine);
                System.out.println("Host: " + host);
                System.out.println("\n...");
            }

            UDPServer.send(response);

            if (verbose) {
                System.out.println("\nResponse sent:");
                System.out.println("HTTP/1.0 " + getStatusCode() + " " + map.get(getStatusCode()));
                System.out.println("Content-type: application/json");
                System.out.println("Content-length: " + response.length());
                System.out.println("\n\n...");
            }
        }}
    }

    public String processRequest(BufferedReader reader) throws IOException {
        // return headers + body
        String response;
        String responseHeader="";
        String responseBody = "";
        String line;
        while((line = reader.readLine()) != null){
            if(line.contains("GET") || line.contains("POST")){
                requestLine = line;
                String[] extractData = line.split(" ");
                String requestMethod= extractData[0];
                String url = extractData[1];
                responseBody = generateResponseBody(requestMethod,url,reader);
                responseHeader = getResponseHeaders(responseBody);
                break;
            }
            if(line.isEmpty()) {
                break;
            }
        }
        response = responseHeader +responseBody;
        return response;
    }

    public String generateResponseBody(String requestMethod, String url, BufferedReader reader) throws IOException {
        setStatusCode(200);
        StringBuilder body = new StringBuilder();
        if(url.startsWith("/")) {
            body.append("{\n");
            String data = "", json = "";
            String args = generateArgs(url);
            String files ="";
            String forms="";
            String status="";
            String content ="";
            String headers = generateHeaders(reader);
            String line=null;
            if(requestMethod.equals("POST")){
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        break;
                    } else if (line.startsWith("{")) {
                        content=line;
                        data = generateData(line);
                        json = generateJSON(line);
                        forms = "  \"form\": {}\n";
                        break;
                    }
                }}
            if(!(url.startsWith("/post") || url.startsWith("/get"))){
                if(requestMethod.equals("GET")){
                    files = processGETRequest_FileStorage(url);
                }
                else if(requestMethod.equals("POST")){
                    status = processPOSTRequest_FileStorage(url,content);
                }
            }
            body.append(args);
            body.append(data);
            body.append(files);
            body.append(forms);
            body.append(status);
            body.append(headers);
            body.append(json);

            String localIP = "";
            String host = getHost().replace(" ","");
            if(host.equals("null"))
                host="localhost";
            try {
                InetAddress localhost = InetAddress.getLocalHost();
                localIP = localhost.getHostAddress();
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
            body.append("\n  \"origin\": \"").append(localIP).append("\",\n");
            body.append("  \"url\":\"http://").append(host).append(url).append("\"");
            body.append("\n}");
            return body.toString();
        }
        return body.toString();

    }

    private String processPOSTRequest_FileStorage(String url,String content) throws IOException {

        StringBuilder responseBody = new StringBuilder();
        responseBody.append("  \"status\":{\n");
        String[] path = url.split("\\?");

        if(path[0].startsWith("/")) {
            String requestedPath = getBaseDirectory() + "/" + path[0];
            Path absolutePath = Paths.get(getBaseDirectory()).resolve(requestedPath).toAbsolutePath().normalize();

            if (absolutePath.startsWith(Paths.get(getBaseDirectory()).toAbsolutePath())) {
                File file = new File(requestedPath+ ".txt");
                if(!file.exists()){
                    setStatusCode(201);
                    responseBody.append("\t").append(file.getName()).append("Doesn't exist, creating a new file,");
                }

                BufferedWriter bw = new BufferedWriter(new FileWriter(file, !overWrite));
                bw.flush();
                bw.write(content);
                bw.close();
                responseBody.append("\n\tContent has been saved to a file");

            } else{
                setStatusCode(403);
                responseBody.append("\t403 Forbidden: Access to this directory is not allowed.");
            }
        }
        responseBody.append("\n  }\n");
        return responseBody.toString();
    }



    private String processGETRequest_FileStorage(String url) {
        StringBuilder responseBody = new StringBuilder();
        String[] path = url.split("\\?");
        String requestedPath = getBaseDirectory() + "/" + path[0];
        Path absolutePath = Paths.get(getBaseDirectory()).resolve(requestedPath).toAbsolutePath().normalize();
        responseBody.append("  \"files\": {");
        if(absolutePath.startsWith(Paths.get(getBaseDirectory()).toAbsolutePath())) {
            if ("/".equals(path[0])) {
                //display all files in the directory
                File fileDirectory = new File(requestedPath);
                File[] files = fileDirectory.listFiles();
                if (files != null && files.length > 0) {
                    for (File file : files) {
                        responseBody.append("\n\t\"").append(file.getName()).append("\",");
                    }
                    responseBody.setLength(responseBody.length() - 1);
                }
                setStatusCode(200);
            } else if (path[0].startsWith("/")) {
                //retrieve the content of the file
                File file = new File(requestedPath+ ".txt");
                if (file.exists() && file.isFile()) {
                    try {
                        BufferedReader reader = new BufferedReader(new FileReader(file));
                        String line = null;
                        responseBody.setLength(0);
                        responseBody.append("  \"data\": {");
                        while ((line = reader.readLine()) != null) {
                            responseBody.append("\n\t").append(line).append(",");
                        }
                        responseBody.setLength(responseBody.length() - 1);
                        reader.close();
                    } catch (IOException e) {
                        setStatusCode(500);
                        responseBody.append("\tInternal error");
                    }
                    setStatusCode(200);
                } else{
                    setStatusCode(404);
                    responseBody.append("\n\t404 Not Found\n\tFile does not exist in the directory");
                }

            }
            else{
                setStatusCode(404);
                responseBody.append("\tDirectory not found");

            }
        }
        else{
            setStatusCode(403);
            responseBody.append("\tHTTP/1.1 403 Forbidden\n\n  Access to this directory is not allowed");
        }
        responseBody.append("\n  },\n");
        return responseBody.toString();
    }

    private String getResponseHeaders(String responseBody) {
        return "HTTP/1.1 " + getStatusCode() + " "+ map.get(getStatusCode()) + "\n" +
                "Date: " + getDate() + "\n" +
                "Content-Type: application/json" + "\n" +
                "Content-Length: " + responseBody.length() + "\n" +
                "Connection: close" + "\n" +
                "Server: gunicorn/19.9.0" + "\n" +
                "Access-Control-Allow-Origin: *" + "\n" +
                "Access-Control-Allow-Credentials: true" + "\n";
    }

    public String generateHeaders(BufferedReader reader) throws IOException {
        StringBuilder headers = new StringBuilder("  \"headers\": {\n");

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                break;
            }
            String[] temp = line.split(":");
            if(line.contains("Host"))
                setHost(temp[1]);
            if(line.startsWith("overwrite")){
                String[] append = line.split("=");
                overWrite= Boolean.parseBoolean(append[1]);
            }
            else{
                if(temp[1].equals("null"))
                    temp[1] = "localhost";
                headers.append("\t\"").append(temp[0]).append("\": \"").append(temp[1]).append("\",\n");
            }
        }
        headers.setLength(headers.length()-1);
        headers.append("\n  },");
        return headers.toString();
    }

    public static String generateJSON(String data){

        StringBuilder json = new StringBuilder();
        json.append("\n  \"json\":{");

        data = data.replace("{","");
        data = data.replace("}","");
        String[] jsonData = data.split(",");

        for(String attribute : jsonData )
            json.append("\n\t").append(attribute).append(",");

        json.setLength(json.length()-1);
        json.append("\n  },");

        return json.toString();
    }

    public static String generateData(String dataAttributes){
        StringBuilder data = new StringBuilder();
        data.append("  \"data\":{");

        dataAttributes = dataAttributes.replace("{","");
        dataAttributes = dataAttributes.replace("}","");
        String[] jsonData = dataAttributes.split(",");

        for(String attribute : jsonData )
            data.append(attribute.replace("\"","\\\"")).append(",");

        data.setLength(data.length()-1);
        data.append("},\n");

        return data.toString();
    }

    public static String generateArgs(String url){

        StringBuilder args = new StringBuilder();
        args.append("\n  \"args\":{");

        String[] extractQueryParameters = url.split("\\?");

        if(extractQueryParameters.length>1) {
            if(!extractQueryParameters[1].equals("null")){
                String[] queryParameters = extractQueryParameters[1].split("&");

                for (String queryParameter : queryParameters) {
                    String[] key_values = queryParameter.split("=");
                    args.append("\n\t\"").append(key_values[0]).append("\": \"").append(key_values[1]).append("\",");
                }
                args.append("\n  ");
            }

        }
        args.append("},\n");

        return args.toString();

    }

    public static String getDate(){
        Date currentDate = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z");
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        String formattedDate = dateFormat.format(currentDate);
        formattedDate = formattedDate.replace(".", "");
        return formattedDate;
    }


}
