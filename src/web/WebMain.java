package web;

import java.io.IOException;

/** Entry point for the web application. Run, then open http://localhost:8080 */
public class WebMain {
    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        new WebServer().start(port);
    }
}
