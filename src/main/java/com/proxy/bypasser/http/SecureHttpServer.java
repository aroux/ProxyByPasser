package com.proxy.bypasser.http;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

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
	private DefaultHttpServerConnection serverConnection;
	private HttpContext context;
	private ServerSocket serverSocket;
	
	public SecureHttpServer(Integer httpServerPort) throws NoSuchAlgorithmException, NoSuchPaddingException, FileNotFoundException, IOException, ClassNotFoundException  {
		super();
		serverSocket = new ServerSocket(httpServerPort);
		serverConnection = new DefaultHttpServerConnection();
		
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
		boolean active = true;
		context.setAttribute("tcpForwarder", tcpForwarder);
		context.setAttribute("secureHttpServer", this);
		while (active) {
			Socket socket = serverSocket.accept();
			serverConnection.bind(socket, params);
			//tcpForwarder.openNewStreams();
			try {
			    while (active && serverConnection.isOpen()) {
			        httpService.handleRequest(serverConnection, context);
			    }
			} catch (IOException e) {
				logger.info("Client " + socket.getRemoteSocketAddress() + " has closed connection");
			} finally {
				serverConnection.shutdown();
				socket.close();
			}
		}
		serverSocket.close();
	}
	
	public void closeServiceStreams() {
		tcpForwarder.closeCurrentStreams();
	}
}
