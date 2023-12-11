package heronarts.lx.websocket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXLoopTask;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedTextMessage;
import io.undertow.websockets.core.StreamSourceFrameChannel;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.spi.WebSocketHttpExchange;

public class LXWebsocketEngine extends LXComponent implements LXLoopTask {

  // TODO - replace horrendous copy/paste with real resource serving
  private static final String PAGE =
    "<html>" +
  "<head><title>Web Socket Test</title></head>" +
  "<body>" +
  "<script>" +
      "var socket;" +
      "if (window.WebSocket) {"+
          "socket = new WebSocket(\"ws://localhost:8080/lx\");"+
          "socket.onmessage = function(event) {" +
              "alert(\"Received data from websocket: \" + event.data);"+
          "};"+
          "socket.onopen = function(event) {"+
              "alert(\"Web Socket opened!\");"+
          "};"+
          "socket.onclose = function(event) {"+
              "alert(\"Web Socket closed.\");"+
          "};"+
      "} else {"+
          "alert(\"Your browser does not support Websockets. (Use Chrome)\");"+
      "}"+
      "function send(message) {"+
          "if (!window.WebSocket) {"+
              "return;"+
          "}"+
          "if (socket.readyState == WebSocket.OPEN) {"+
              "socket.send(message);"+
          "} else {"+
              "alert(\"The socket is not open.\");"+
          "}"+
      "}"+
      "function closeSocket() {"+
        "if (!window.WebSocket) {"+
            "return;"+
        "}"+
        "if (socket.readyState == WebSocket.OPEN) {"+
          "socket.close();"+
        "} else {"+
            "alert(\"The socket is not open.\");"+
        "}"+
      "}"+
  "</script>"+
  "<form onsubmit=\return false;\">"+
      "<input type=\"text\" name=\"message\" value=\"Hello, World!\"/>"+
      "<input type=\"button\" value=\"Send Web Socket Data\" onclick=\"send(this.form.message.value)\"/>"+
      "<input type=\"button\" value=\"Close\" onclick=\"closeSocket()\"/>"+
  "</form>"+
  "</body>"+
  "</html>";

  private final Undertow server;

  private class Request {
    final WebSocketChannel channel;
    final String message;

    Request(WebSocketChannel channel, String message) {
      this.channel = channel;
      this.message = message;
    }
  }

  private final List<Request> serverThreadRequests = new ArrayList<Request>();
  private final List<Request> lxThreadRequests = new ArrayList<Request>();

  public LXWebsocketEngine(LX lx) {
    super(lx);

    // TODO - is this what we want? Better way to specify?
    System.setProperty("org.jboss.logging.provider", "slf4j");

    this.server = Undertow.builder()
      .addHttpListener(8080, "localhost")
      .setHandler(
        Handlers.path()

        // Websocket
        .addPrefixPath("/lx", Handlers.websocket(new WebSocketConnectionCallback() {

          @Override
          public void onConnect(WebSocketHttpExchange exchange, WebSocketChannel channel) {
            LX.log("Websocket client connected");
            channel.getReceiveSetter().set(new AbstractReceiveListener() {
              @Override
              protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage message) {
                synchronized (serverThreadRequests) {
                  // This request comes in on the websocket server thread. We can't go diving into the
                  // LX API from here, it could be changing in parallel. Shuffle these requests into a
                  // work queue for the engine thread
                  serverThreadRequests.add(new Request(channel, message.getData()));
                }
              }

              @Override
              protected void onError(WebSocketChannel channel, Throwable error) {
                error(error, "Websocket client error");
                super.onError(channel, error);
              }

              @Override
              protected void onClose(WebSocketChannel webSocketChannel, StreamSourceFrameChannel channel) throws IOException {
                log("Websocket client closed");
                super.onClose(webSocketChannel, channel);
              }

            });
            channel.resumeReceives();
          }
        }))

        // Root serves up the dummy page
        .addPrefixPath("/", new HttpHandler() {
          @Override
          public void handleRequest(final HttpServerExchange exchange) throws Exception {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/html");
            exchange.getResponseSender().send(PAGE);
          }
        })
      ).build();

    LX.log("Starting websocket server");
    this.server.start();
  }

  @Override
  public void loop(double deltaMs) {
    // Move the requests from the websocket server thread onto the LX engine thread where we can
    // access LX hierarchy data structures safely.
    this.lxThreadRequests.clear();
    synchronized (this.serverThreadRequests) {
      this.lxThreadRequests.addAll(this.serverThreadRequests);
      this.serverThreadRequests.clear();
    }
    for (Request request : this.lxThreadRequests) {
      WebSockets.sendText("Echoing the input:" + request.message, request.channel, null);
    }

  }

  @Override
  public void dispose() {
    this.server.stop();
    log("Stopped websocket server");
    super.dispose();
  }

  private static final String WEBSOCKET_LOG_PREFIX = "[Websocket] ";

  public static final void log(String message) {
    LX.log(WEBSOCKET_LOG_PREFIX + message);
  }

  public static final void error(String message) {
    LX.error(WEBSOCKET_LOG_PREFIX + message);
  }

  public static final void error(Throwable x, String message) {
    LX.error(x, WEBSOCKET_LOG_PREFIX + message);
  }
}
