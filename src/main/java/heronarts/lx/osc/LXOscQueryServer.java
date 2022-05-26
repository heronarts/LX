package heronarts.lx.osc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXPath;
import heronarts.lx.parameter.LXParameter;

public class LXOscQueryServer {

  private final LX lx;
  private ServerThread thread = null;

  public LXOscQueryServer(LX lx) {
    this.lx = lx;
  }

  public void bind(int port) {
    unbind();
    this.thread = new ServerThread(port);
  }

  public void unbind() {
    if (this.thread != null) {
      this.thread.interrupt();
      this.thread.dispose();
      this.thread = null;
    }
  }

  private class ServerThread extends Thread {

    private ServerSocket serverSocket = null;
    private boolean closing = false;
    private final int port;

    private ServerThread(int port) {
      this.port = port;
      try {
        this.serverSocket = new ServerSocket();
        this.serverSocket.setReuseAddress(true);
        start();
      } catch (IOException iox) {
        LX.error("Could not create LXOscQueryServer server socket", iox);
        this.serverSocket = null;
      }
    }

    @Override
    public void run() {
      try {
        LXOscEngine.log("Binding LXOscQueryServer on port " + this.port);
        this.serverSocket.bind(new InetSocketAddress(InetAddress.getLocalHost(), this.port));
        while (!isInterrupted()) {
          Socket socket = serverSocket.accept();
          handleClient(socket);
        }
      } catch (IOException iox) {
        if (!this.closing) {
          LX.error(iox, "LXOscQueryServer IO error");
        }
      } catch (Exception x) {
        LX.error(x, "LXOscQueryServer unexpected error");
      }
      dispose();
    }

    public void dispose() {
      if (this.serverSocket != null) {
        try {
          this.closing = true;
          this.serverSocket.close();
        } catch (IOException iox) {
          LX.error(iox, "Error closing LXOscQueryServer server socket");
        } finally {
          this.serverSocket = null;
        }
        LXOscEngine.log("Closed LXOscQueryServer on port " + this.port);
      }
    }

    private void handleClient(Socket socket) throws IOException {
      // This socket gets max 1 second to utilize the OSC query thread
      socket.setSoTimeout(1000);
      InputStream is = socket.getInputStream();
      OutputStream os = socket.getOutputStream();
      try (InputStreamReader isr = new InputStreamReader(is);
           BufferedReader br = new BufferedReader(isr);) {
        String line;
        String get = null;
        URI uri = null;
        while ((line = br.readLine()) != null) {
          if (line.startsWith("GET")) {
            get = line;
            String[] parts = line.split(" ");
            if (parts.length >= 1) {
              try {
                uri = new URI(parts[1]);
              } catch (URISyntaxException urisx) {
                LX.error(urisx, "Bad URI syntax: " + parts[1]);
                uri = null;
              }
            }
          } else if (line.isEmpty()) {
            // End of HTTP request, \r\n pair
            if (uri == null) {
              LX.error("Did not find a URI in HTTP request, closing: " + get);
              break;
            } else {
              sendResponse(uri, os);
            }
            get = null;
            uri = null;
          }
          // Other lines are HTTP headers, we ignore them all for now...
        }
      } catch (SocketTimeoutException stx) {
        // No big deal, we're done...
      }

      // Connection was closed by the other side, or we're bailing out
      is.close();
      os.close();
      socket.close();
    }

    private class OscQueryResponse {
      private boolean ready = false;
      private String rc = "200 OK";
      private JsonObject obj = new JsonObject();
    }

    private void sendResponse(final URI uri, OutputStream os) throws IOException {
      final OscQueryResponse response = new OscQueryResponse();
      if ("HOST_INFO".equals(uri.getQuery())) {
        response.obj.addProperty("NAME", "LX:" + this.port);
        response.obj.addProperty("OSC_PORT", this.port);
        JsonObject extensions = new JsonObject();
        extensions.addProperty("VALUE", true);
        extensions.addProperty("DESCRIPTION", true);
        response.obj.add("EXTENSIONS", extensions);
      } else {
        // We need to iterate through the LX hierarchy...
        // prepare the OSC query response on the LX engine thread
        lx.engine.addTask(() -> {
          if ("/".equals(uri.getPath())) {
            response.obj.addProperty("FULL_PATH", "/");
            response.obj.addProperty("DESCRIPTION", "Root Node");
            JsonObject contents = new JsonObject();
            contents.add("lx", lx.engine.toOscQuery());
            response.obj.add("CONTENTS", contents);
          } else {
            LXPath path = LXPath.get(lx, uri.getPath());
            if (path == null) {
              response.rc = "404 NOT FOUND";
            } else if (path instanceof LXComponent) {
              response.obj = ((LXComponent) path).toOscQuery();
            } else if (path instanceof LXParameter) {
              response.obj = path.getParent().toOscQuery((LXParameter) path);
            }
          }
          // Notify the HTTP server thread that the goods are good to go
          synchronized (response) {
            response.ready = true;
            response.notify();
          }
        });

        // Wait for the LX engine thread to prepare the response object
        synchronized (response) {
          if (!response.ready) {
            try {
              response.wait();
            } catch (InterruptedException ix) {
              // ignore it...
            }
          }
        }
      }

      String json = new Gson().toJson(response.obj);
      byte[] bytes = json.getBytes("UTF-8");

      String httpHeader =
        "HTTP/1.1 " + response.rc + "\r\n" +
        "Content-Type: application/json\r\n"+
        "Connection: keep-alive\r\n"+
        "Content-Length: " + bytes.length + "\r\n\r\n";

      os.write(httpHeader.getBytes());
      os.write(bytes);
      os.flush();
    }
  }
}


