package com.proxy.bypasser.http;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

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
	
	private ServerSocket serverSocket;
	
	private boolean active;
	
	private HashMap<String, ServerConnectionHandler> handlers;
	
	private HashMap<String, TcpForwarder> tcpForwarders;
	
	private TcpForwarderGarbageCollector tfgc;
	
	private Long garbageTriggerTime;
	
	public SecureHttpServer(Integer httpServerPort) throws NoSuchAlgorithmException, NoSuchPaddingException, FileNotFoundException, IOException, ClassNotFoundException  {
		super();
		serverSocket = new ServerSocket(httpServerPort);
		handlers = new HashMap<String, ServerConnectionHandler>();
		tcpForwarders = new HashMap<String, TcpForwarder>();
	}
	
	public void run() throws IOException, HttpException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
		tfgc = new TcpForwarderGarbageCollector(garbageTriggerTime);
		tfgc.start();
		active = true;
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
	
	public void setGarbageTriggerTime(Long garbageTriggerTime) {
		this.garbageTriggerTime = garbageTriggerTime;
	}
	
	public void shutdownServer() {
		logger.info("Server is shutting down...");
		active = false;
		@SuppressWarnings("unchecked")
		HashMap<String, ServerConnectionHandler> handlersCopy = (HashMap<String, ServerConnectionHandler>) handlers.clone();
		for (ServerConnectionHandler handler : handlersCopy.values()) {
			handler.shutdown();
		}
		tfgc.shutdown();
		logger.info("Shutting down completed.");
	}
	
	class ServerConnectionHandler extends Thread {
		
		private Socket socket;
		
		private boolean active;
		
		private String socketId;
		
		private DefaultHttpServerConnection serverConnection;
		
		private HttpService httpService;
		private HttpParams params;
		private HttpProcessor httpProc;
		private HttpContext context;
		
		public ServerConnectionHandler(Socket socket, String socketId) {
			this.socket = socket;
			this.socketId = socketId;
			
			params = new BasicHttpParams();
			HttpProtocolParamBean paramsBean = new HttpProtocolParamBean(params);
			paramsBean.setContentCharset("UTF-8");
			paramsBean.setVersion(HttpVersion.HTTP_1_1);
			
			httpProc = new BasicHttpProcessor();
			context = new BasicHttpContext();

			HttpRequestHandlerRegistry handlerResolver = 
			    new HttpRequestHandlerRegistry();
			handlerResolver.register("*", new SecureServerHttpRequestHandler());
			
			httpService = new HttpService(
			        httpProc, 
			        new DefaultConnectionReuseStrategy(), 
			        new DefaultHttpResponseFactory(),
			        handlerResolver,
			        params);
			
			context.setAttribute("secureHttpServer", SecureHttpServer.this);
			context.setAttribute("tcpForwarders", tcpForwarders);
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
	
	class TcpForwarderGarbageCollector extends Thread {
		
		private Long garbageTriggerTime;
		
		private Integer sleepTime = 1000;
		
		private boolean active;
		
		public TcpForwarderGarbageCollector(Long garbageTriggerTime) {
			this.garbageTriggerTime = garbageTriggerTime;
		}
		
		@Override
		public void run() {
			active = true;
			while (active) {
				
				long currentTime = new Date().getTime();
				synchronized (tcpForwarders) {
					List<String> tcpForwardersToRemove = new ArrayList<String>();
					for (Entry<String, TcpForwarder> entry : tcpForwarders.entrySet()) {
						TcpForwarder tcpForwarder = entry.getValue();
						long diffTime = currentTime - tcpForwarder.getLastUsedTime();
						if (diffTime > garbageTriggerTime.longValue()) {
							logger.info("TcpForwarder with service instance id '" + tcpForwarder.getServiceInstanceId() + "' marked for removal.");
							tcpForwarder.shutdown();
							tcpForwardersToRemove.add(tcpForwarder.getServiceInstanceId());
						}
					}
					for (String serviceInstanceId : tcpForwardersToRemove) {
						tcpForwarders.remove(serviceInstanceId);
					}
				}
				
				try {
					Thread.sleep(sleepTime);
				} catch (InterruptedException e) {
					logger.info("Sleep interrupted. Will likely shutdown.");
				}
			}
		}
		
		public void shutdown() {
			this.active = false;
			this.interrupt();
		}
	}
}
