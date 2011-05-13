package com.proxy.bypasser.tcp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Date;

import org.apache.log4j.Logger;

import com.proxy.bypasser.data.BytesArray;
import com.proxy.bypasser.io.exc.SocketClosedException;
import com.proxy.bypasser.utils.IOUtils;

public class TcpForwarder {
	
	Logger logger = Logger.getLogger(TcpForwarder.class);
	
	private Socket socket;
	private InputStream is;
	private OutputStream os;
	
	private String prefixService;
	private String serviceInstanceId;
	
	private long lastUsedTime;
	
	public TcpForwarder() {
		this.prefixService = null;
	}
	
	public TcpForwarder(Socket socket, String prefixService) throws IOException {
		this.socket = socket;
		is = socket.getInputStream();
		os = socket.getOutputStream();
		this.prefixService = prefixService;
	}

	
	public void openNewStreams(String serviceUrl, Integer servicePort) throws IOException {
		InetAddress serviceAddress = InetAddress.getByName(serviceUrl);
		socket = new Socket(serviceAddress, servicePort);
		is = socket.getInputStream();
		os = socket.getOutputStream();
		logger.info(prefixMessageWithService("New streams open to " + serviceUrl + ":" + servicePort));
		lastUsedTime = new Date().getTime();
	}
	

	public boolean canRead() {
		if (socket == null) {
			return false;
		}
		return !socket.isClosed() && !socket.isInputShutdown();
	}
	
	public BytesArray readBytesArray(boolean doNotCheckAvailableFirstRead) throws IOException {
		lastUsedTime = new Date().getTime();
		try {
			BytesArray data = IOUtils.readAvailableFromIS(is, doNotCheckAvailableFirstRead);
			logger.info(prefixMessageWithService("Received " + data.getSize() + " bytes from service @" + socket.getRemoteSocketAddress()));
			return data;
		} catch (SocketClosedException e) {
			logger.info(prefixMessageWithService("Socket has been closed. Closing current stream."));
			closeCurrentStreams();
			return new BytesArray(null, 0);
		}
	}
	
	public void writeBytesArray(BytesArray data) throws IOException {
		lastUsedTime = new Date().getTime();
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
	}
	
	public String prefixMessageWithService(Object message) {
		if (prefixService == null) {
			return message.toString();
		} else {
			if (serviceInstanceId == null) {
				return prefixService + message.toString();
			} else {
				return prefixService + "[SI " + serviceInstanceId  + "] " + message.toString();
			}
		}
	}
	
	public void setServiceInstanceId(String serviceInstanceId) {
		this.serviceInstanceId = serviceInstanceId;
	}
	
	public void setPrefixService(String prefixService) {
		this.prefixService = prefixService;
	}
	
	public long getLastUsedTime() {
		return lastUsedTime;
	}
	
	public String getServiceInstanceId() {
		return serviceInstanceId;
	}
}
