package com.proxy.bypasser.http;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.apache.http.HttpException;
import org.apache.http.HttpVersion;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.DefaultHttpServerConnection;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParamBean;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestHandlerRegistry;
import org.apache.http.protocol.HttpService;
import org.apache.log4j.Logger;

import com.proxy.bypasser.tcp.TcpForwarder;

public class SecureHttpServer extends SecureHttpPeer {
	
	Logger logger = Logger.getLogger(SecureServerHttpRequestHandler.class);
	
	private HttpService httpService;
	private HttpParams params;
	private HttpProcessor httpProc;
	private HttpContext context;
	private ServerSocket serverSocket;
	
	private boolean active;
	
	private HashMap<String, ServerConnectionHandler> handlers;
	
	public SecureHttpServer(Integer httpServerPort) throws NoSuchAlgorithmException, NoSuchPaddingException, FileNotFoundException, IOException, ClassNotFoundException  {
		super();
		serverSocket = new ServerSocket(httpServerPort);
		handlers = new HashMap<String, ServerConnectionHandler>();
		
		params = new BasicHttpParams();
		HttpProtocolParamBean paramsBean = new HttpProtocolParamBean(params);
		paramsBean.setContentCharset("UTF-8");
		paramsBean.setVersion(HttpVersion.HTTP_1_1);
		
		httpProc = new BasicHttpProcessor();
		context = new BasicHttpContext();

		HttpRequestHandlerRegistry handlerResolver = 
		    new HttpRequestHandlerRegistry();
		handlerResolver.register("*", new SecureServerHttpRequestHandler());
		
		tcpForwarder = new TcpForwarder();
		
		httpService = new HttpService(
		        httpProc, 
		        new DefaultConnectionReuseStrategy(), 
		        new DefaultHttpResponseFactory(),
		        handlerResolver,
		        params);
	}
	
	public void run() throws IOException, HttpException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
		active = true;
		context.setAttribute("tcpForwarder", tcpForwarder);
		context.setAttribute("secureHttpServer", this);
		while (active) {
			logger.info("Listening for new connection.");
			Socket socket = serverSocket.accept();
			String socketId = socket.getRemoteSocketAddress().toString();
			ServerConnectionHandler sch = new ServerConnectionHandler(socket, socketId);
			logger.info("Register ServerConnectionHandler with id " + socketId);
			handlers.put(socketId, sch);
			sch.start();
		}
		serverSocket.close();
	}
	
	public void shutdownServer() {
		logger.info("Server is shutting down...");
		active = false;
		@SuppressWarnings("unchecked")
		HashMap<String, ServerConnectionHandler> handlersCopy = (HashMap<String, ServerConnectionHandler>) handlers.clone();
		for (ServerConnectionHandler handler : handlersCopy.values()) {
			handler.shutdown();
		}
		logger.info("Shutting down completed.");
	}
	
	public void closeServiceStreams() {
		tcpForwarder.closeCurrentStreams();
	}
	
	class ServerConnectionHandler extends Thread {
		
		private Socket socket;
		
		private boolean active;
		
		private String socketId;
		
		private DefaultHttpServerConnection serverConnection;
		
		public ServerConnectionHandler(Socket socket, String socketId) {
			this.socket = socket;
			this.socketId = socketId;
		}
		
		@Override
		public void run() {
			active = true;
			serverConnection = new DefaultHttpServerConnection();
			logger.info("New connection with " + socket.getRemoteSocketAddress() + " open.");
			try {
				serverConnection.bind(socket, params);
			    while (active && serverConnection.isOpen()) {
			        httpService.handleRequest(serverConnection, context);
			    }
			} catch (IOException e) {
				logger.info("Client " + socket.getRemoteSocketAddress() + " has closed connection");
			} catch (HttpException e) {
				logger.error("Http error with client " + socket.getRemoteSocketAddress(), e);
			} finally {
				try {
					serverConnection.shutdown();
					socket.close();
				} catch (IOException e) {
					logger.error("Impossible to shutdown ServerConnection.", e);
				}
			}
			
			logger.info("Unregister ServerConnectionHandler with id " + socketId);
			SecureHttpServer.this.handlers.remove(socketId);
		}
		
		public void shutdown() {
			this.active = false;
			try {
				serverConnection.shutdown();
			} catch (IOException e) {
				logger.error("Impossible to shutdown ServerConnection.", e);
			}
		}
	}
}
