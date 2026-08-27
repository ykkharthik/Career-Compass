package web;

import java.io.IOException;

/**
 * Entry point for the web application. Run, then open http://localhost:8080
 *
 * Port resolution, in priority order: an explicit command-line argument
 * (local dev convenience, e.g. "java ... web.WebMain 9090"), then the
 * $PORT environment variable (the convention Render, Railway, Heroku and
 * similar platforms use to tell a container which port to bind), then
 * 8080 as the local default.
 */
public class WebMain {
    public static void main(String[] args) throws IOException {
        int port = 8080;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        } else {
            String envPort = System.getenv("PORT");
            if (envPort != null && !envPort.isBlank()) port = Integer.parseInt(envPort.trim());
        }
        new WebServer().start(port);
    }
}
