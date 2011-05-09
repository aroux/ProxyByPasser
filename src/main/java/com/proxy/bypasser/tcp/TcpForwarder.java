package com.proxy.bypasser.tcp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.log4j.Logger;

import com.proxy.bypasser.data.BytesArray;
import com.proxy.bypasser.http.SecureHttpClient;
import com.proxy.bypasser.io.exc.FatalIOException;
import com.proxy.bypasser.utils.IOUtils;

public class TcpForwarder {
	
	Logger logger = Logger.getLogger(TcpForwarder.class);
	
	private Integer port;
	
	private Socket socket;
	private ServerSocket serverSocket = null;
	
	private SecureHttpClient client;
	
	private InputStream is;
	
	private OutputStream os;
	
	public TcpForwarder() {
		this.port = null;
		this.client = null;
	}
	
	public TcpForwarder(Integer port, SecureHttpClient client) {
		this.port = port;
		this.client = client;
	}
	
	public void waitForNextIncomingConnection() throws IOException {
		try {
			if (serverSocket == null) {
				serverSocket = new ServerSocket(port);
			}
			socket = serverSocket.accept();
		} catch (IOException e) {
			throw new FatalIOException(e);
		}
		is = socket.getInputStream();
		os = socket.getOutputStream();
	}
	
	public void openNewStreams(String serviceUrl, Integer servicePort) throws IOException {
		InetAddress serviceAddress = InetAddress.getByName(serviceUrl);
		socket = new Socket(serviceAddress, servicePort);
		is = socket.getInputStream();
		os = socket.getOutputStream();
		logger.info(prefixMessageWithService("New streams open to " + serviceUrl + ":" + servicePort));
	}
	

	public boolean canRead() {
		if (socket == null) {
			return false;
		}
		return !socket.isInputShutdown();
	}
	
	public BytesArray readBytesArray(boolean doNotCheckAvailableFirstRead) throws IOException {
		BytesArray data = IOUtils.readAvailableFromIS(is, doNotCheckAvailableFirstRead);
		logger.info(prefixMessageWithService("Received " + data.getSize() + " bytes from service @" + socket.getRemoteSocketAddress()));
		return data;
	}
	
	public void writeBytesArray(BytesArray data) throws IOException {
		os.write(data.getData(), 0, data.getSize());
		os.flush();
		logger.info(prefixMessageWithService("Sent " + data.getSize() + " bytes to service @" + socket.getRemoteSocketAddress()));
	}
	
	public void closeCurrentStreams() {
		if (is != null) {
			try {
				is.close();
			} catch (IOException e) {
				logger.error(prefixMessageWithService("Impossible to close input stream."), e);
			}
			is = null;
		}
		if (os != null) {
			try {
				os.close();
			} catch (IOException e) {
				logger.error(prefixMessageWithService("Impossible to close output stream."), e);
			}
			os = null;
		}
		if (socket != null) {
			try {
				socket.close();
			} catch (IOException e) {
				logger.error(prefixMessageWithService("Impossible to close socket."), e);
			}
			socket = null;
		}
		
		logger.info(prefixMessageWithService("Current stream closed."));
	}
	
	public void shutdown() {
		closeCurrentStreams();
		try {
			if (serverSocket != null) {
				serverSocket.close();
			}
		} catch (IOException e) {
			logger.error("Impossible to close server socket.", e);
		}
	}
	
	private String prefixMessageWithService(Object message) {
		if (client != null) {
			return client.prefixMessageWithService(message);
		} else {
			return message.toString();
		}
	}
}
