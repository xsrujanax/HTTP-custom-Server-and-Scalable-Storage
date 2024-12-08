// ..........................................
// Assignment # 2!
// © @Srujana Guttula - 40237663 , @Aakansha -  40188693
//
// ..........................................

import java.io.IOException;

public class https {
    private static final int DEFAULT_PORT = 8080;
    private static final String DEFAULT_DIRECTORY= "C:\\Users\\Sruja\\GIT\\HTTPServer\\SimpleStorage";

    public static void main(String[] args) throws IOException {
        int port = DEFAULT_PORT;
        String baseDirectory = DEFAULT_DIRECTORY;
        boolean verbose = false;

        // Parse command-line arguments
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-v":
                    verbose = true;
                    break;
                case "-p":
                    i++;
                    if (i < args.length) {
                        port = Integer.parseInt(args[i]);
                    }

                    break;
                case "-d":
                    i++;
                    if (i < args.length) {
                        baseDirectory = args[i];
                    }
                    break;
            }
        }
        HTTPFileStorage server = new HTTPFileStorage(port, baseDirectory, verbose);
        //HTTPFileStorage server = new HTTPFileStorage(80, baseDirectory, true);
        server.startServer();
    }
}
