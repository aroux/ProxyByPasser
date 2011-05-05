package com.proxy.bypasser.tcp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.log4j.Logger;

import com.proxy.bypasser.data.BytesArray;
import com.proxy.bypasser.io.exc.FatalIOException;
import com.proxy.bypasser.utils.IOUtils;

public class TcpForwarder {
	
	Logger logger = Logger.getLogger(TcpForwarder.class);
	
	private Integer socketServerPort = 5555;
	
	private Socket socket;
	private ServerSocket serverSocket = null;
	
	private InputStream is;
	
	private OutputStream os;
	
	public TcpForwarder() {
	}
	
	
	public void waitForNextIncomingConnection() throws IOException {
		if (serverSocket == null) {
			serverSocket = new ServerSocket(socketServerPort);
		}
		try {
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
		logger.info("New streams open to " + serviceUrl + ":" + servicePort);
	}
	

	public boolean canRead() {
		if (socket == null) {
			return false;
		}
		return !socket.isInputShutdown();
	}
	
	public BytesArray readBytesArray(boolean doNotCheckAvailableFirstRead) throws IOException {
		BytesArray data = IOUtils.readAvailableFromIS(is, doNotCheckAvailableFirstRead);
		logger.info("Received " + data.getSize() + " bytes from service @" + socket.getRemoteSocketAddress());
		return data;
	}
	
	public void writeBytesArray(BytesArray data) throws IOException {
		os.write(data.getData(), 0, data.getSize());
		os.flush();
		logger.info("Sent " + data.getSize() + " bytes to service @" + socket.getRemoteSocketAddress());
	}
	
	public void closeCurrentStreams() {
		if (is != null) {
			try {
				is.close();
			} catch (IOException e) {
				logger.error("Impossible to close input stream.", e);
			}
			is = null;
		}
		if (os != null) {
			try {
				os.close();
			} catch (IOException e) {
				logger.error("Impossible to close output stream.", e);
			}
			os = null;
		}
		if (socket != null) {
			try {
				socket.close();
			} catch (IOException e) {
				logger.error("Impossible to close socket.", e);
			}
			socket = null;
		}
	}
	
	public void shutdown() {
		closeCurrentStreams();
		try {
			serverSocket.close();
		} catch (IOException e) {
			logger.error("Impossible t close server socket.", e);
		}
	}
}
