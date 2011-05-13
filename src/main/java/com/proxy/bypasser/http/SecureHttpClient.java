package com.proxy.bypasser.http;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.log4j.Logger;

import com.proxy.bypasser.data.BytesArray;
import com.proxy.bypasser.data.Request;
import com.proxy.bypasser.data.Response;
import com.proxy.bypasser.services.ServiceInfo;
import com.proxy.bypasser.tcp.TcpForwarder;

public class SecureHttpClient extends SecureHttpPeer implements Cloneable, Runnable {
	
	private ServerSocket serverSocket;
	
	private String serverUrl;
	private Integer serverPort; 
		
	private boolean running;
	
	private Boolean proxyEnabled;
	private String proxyUrl;
	private Integer proxyPort;
	
	private int maxPollerSleepTime;
	private int minPollerSleepTime;
	private int incrementPollerSleepTime;
	
	protected Logger logger = Logger.getLogger(SecureHttpClient.class);
	
	protected ServiceInfo serviceInfo;
	
	private HashMap<String, ClientConnectionHandler> handlers;
	
	public SecureHttpClient() throws NoSuchAlgorithmException, NoSuchPaddingException, FileNotFoundException, IOException, ClassNotFoundException  {
		super();
		handlers = new HashMap<String, ClientConnectionHandler>();
	}
	
	@Override
	public void run()  {
		running = true;
		logger.info(prefixMessageWithService("Running secure http client for service " + serviceInfo.getUrl() + ":" + serviceInfo.getPort() //
				+ ". Listening on port " + serviceInfo.getSecureClientPort() + "."));
		try {
			serverSocket = new ServerSocket(serviceInfo.getSecureClientPort());
			while (running) {
				Socket socket = serverSocket.accept();
				try {
					String socketId = socket.getRemoteSocketAddress().toString();
					ClientConnectionHandler cch = new ClientConnectionHandler(socket, socketId);
					logger.info(prefixMessageWithService("Register ClientrConnectionHandler with id " + socketId));
					handlers.put(socketId, cch);
					cch.start();
				} catch (IOException e) {
					logger.fatal(prefixMessageWithService("IO Exception when creating Client connection handler. Skipping connection."), e);
				}
			}
		} catch (IOException e) {
			logger.fatal(prefixMessageWithService("Fatal IO Exception. Shutdown all."), e);
			running = false;
		}
		shutdown();
	}

	
	private void shutdown() {
		try {
			serverSocket.close();
		} catch (IOException e) {
			logger.error("Impossible to close server socket.", e);
		}
	}
	
	private class ClientConnectionHandler extends Thread {
		private TcpForwarder tcpForwarder;
		
		private HttpClient httpClient;
		private HttpContext httpContext;
		
		private HttpDataPoller poller;
		
		private String socketId;
		private String serviceInstanceId;
		
		public ClientConnectionHandler(Socket socket, String socketId) throws IOException {
			this.socketId = socketId;
			generatingServiceInstanceId();
			tcpForwarder = new TcpForwarder(socket, prefixMessageWithService(""));
			tcpForwarder.setServiceInstanceId(serviceInstanceId);
		}
		
		private void generatingServiceInstanceId() {
			Date date = new Date();
			Long time = date.getTime();
			
			Random rand = new Random(time);
			Long randLong = rand.nextLong();
			serviceInstanceId = serviceInfo.getUrl() + ":" + serviceInfo.getPort() + "|" + time + randLong;
		}
		
		@Override
		public void run() {
			try {
				init();
			} catch (Exception e1) {
				logger.fatal(tcpForwarder.prefixMessageWithService("Impossible to init SecureHttpClient. Exiting."));
				return;
			}
			
			try {
				// To be sure that the service is not already connected server side
				// and therefore in a invalid state, we ask the server to close its
				// potential connection.
				sendRequestWithoutData(Request.RequestType.REINIT_SERVICE_STREAM);
				while (tcpForwarder.canRead()) {
					if (poller == null) {
						poller = new HttpDataPoller(maxPollerSleepTime, minPollerSleepTime, incrementPollerSleepTime);
						poller.start();
					}
					BytesArray data = tcpForwarder.readBytesArray(true);
					if (data.getSize() > 0) {
						synchronized (this) {
							if (poller != null) poller.interruptPolling();
							BytesArray decryptedData = sendForwardRequest(data);
							if (decryptedData.getSize() > 0) {
								tcpForwarder.writeBytesArray(decryptedData);
							}
						}
					}
				}
				shutdown();
			} catch (InvalidKeyException e) {
				logger.fatal(tcpForwarder.prefixMessageWithService("Encrypt/decrypt error."), e);
				shutdown();
			} catch (BadPaddingException e) {
				logger.fatal(tcpForwarder.prefixMessageWithService("Encrypt/decrypt error."), e);
				shutdown();
			} catch (IllegalBlockSizeException e) {
				logger.fatal(tcpForwarder.prefixMessageWithService("Encrypt/decrypt error."), e);
				shutdown();
			} catch (IllegalStateException e) {
				logger.fatal(tcpForwarder.prefixMessageWithService("Encrypt/decrypt error."), e);
				shutdown();
			} catch (ClassNotFoundException e) {
				logger.fatal(tcpForwarder.prefixMessageWithService("Impossible to deserialize request."), e);
				shutdown();
			} catch (IOException e) {
				logger.fatal(tcpForwarder.prefixMessageWithService("Problem with IO."), e);
				shutdown();
			}
		}
			
		private void init() throws Exception {
			Scheme http = new Scheme("http", serverPort, PlainSocketFactory.getSocketFactory());
			SchemeRegistry sr = new SchemeRegistry();
			sr.register(http);
			ClientConnectionManager connMrg = new SingleClientConnManager(sr);
			httpClient = new DefaultHttpClient(connMrg);
			if (proxyEnabled) {
				initProxyConfiguration();
			}
			httpContext = new BasicHttpContext();
		}
		
		private void initProxyConfiguration() {
			HttpHost proxy = new HttpHost(proxyUrl, proxyPort);
			httpClient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
		}
		
		private BytesArray sendForwardRequest(BytesArray data) throws InvalidKeyException, BadPaddingException, IllegalBlockSizeException, IllegalStateException, IOException, ClassNotFoundException {
			HttpPost httpPost = new HttpPost("http://" + serverUrl + ":" + serverPort);
			Request req = new Request(Request.RequestType.FORWARD, new BytesArray(data.getData(), data.getSize()), serviceInfo, serviceInstanceId);
			httpPost.setEntity(genEncryptedEntityFromObject(req));
			logger.info(tcpForwarder.prefixMessageWithService("Forwarding " + req.getData().getSize() + " bytes to secure http server"));
			HttpResponse response = httpClient.execute(httpPost, httpContext);
			return getDataFromResponse(response);
		}
		
		private BytesArray sendRequestWithoutData(Request.RequestType type) throws IOException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException, IllegalStateException, ClassNotFoundException {
			HttpPost httpPost = new HttpPost("http://" + serverUrl + ":" + serverPort);
			Request req = new Request(type, new BytesArray(null, 0), serviceInfo, serviceInstanceId);
			httpPost.setEntity(genEncryptedEntityFromObject(req));
			logger.info(tcpForwarder.prefixMessageWithService("Sending 'ASK_FOR_DATA' request of " + req.getData().getSize() + " bytes to secure http server"));
			HttpResponse response = httpClient.execute(httpPost, httpContext);
			return getDataFromResponse(response);
		}
		
		private BytesArray getDataFromResponse(HttpResponse response) throws IOException, IllegalStateException, ClassNotFoundException {
			HttpEntity entity = response.getEntity();
			Response internalResponse = readResponseFromEntity(entity);
			logger.info(tcpForwarder.prefixMessageWithService("Received " + internalResponse.getData().getSize() + " bytes from secure http server."));
			return internalResponse.getData();
		}
		
		private void shutdown() {
			if (poller != null) {
				poller.shutdown();
				poller = null;
			}
			tcpForwarder.shutdown();
			logger.info(tcpForwarder.prefixMessageWithService("Unregister ClientConnectionHandler with id " + socketId));
			SecureHttpClient.this.handlers.remove(socketId);
			httpClient.getConnectionManager().shutdown();
		}
		
		private class HttpDataPoller extends Thread {
			
			private AtomicInteger milliBeforeNextPolling;
			
			private boolean interrupted = false;
			
			private boolean running = true;
			
			private int maxSleepTime;
			private int minSleepTime;
			private int incrementSleepTime;
			
			public HttpDataPoller(int maxSleepTime, int minSleepTime, int incrementSleepTime) {
				this.maxSleepTime = maxSleepTime;
				this.minSleepTime = minSleepTime;
				this.incrementSleepTime = incrementSleepTime;
				milliBeforeNextPolling = new AtomicInteger(minSleepTime);
			}
			
			public void interruptPolling() {
				this.milliBeforeNextPolling.set(minSleepTime);
				this.interrupted = true;
				this.interrupt();
			}
			
			private void processError(String errorText, Exception e) {
				logger.fatal(prefixMessageWithService(errorText), e);
				shutdown();
				running = false;
			}
			
			public void shutdown() {
				running = false;
				interruptPolling();
			}
			
			@Override
			public void run() {
				super.run();
				
				logger.info(prefixMessageWithService("Start HttpDataPoller."));
				
				while (running) {
					try {
						synchronized (ClientConnectionHandler.this) {
							interrupted = false;
						}
						logger.debug(tcpForwarder.prefixMessageWithService("Sleep for " + milliBeforeNextPolling.get() + " milliseconds."));
						Thread.sleep(milliBeforeNextPolling.get());
					} catch (InterruptedException e) {
						logger.debug(tcpForwarder.prefixMessageWithService("Sleep interrupted."));
					}
					
					synchronized(ClientConnectionHandler.this) {
						if (!interrupted) {
							try {
								logger.debug(tcpForwarder.prefixMessageWithService("Ask for data to remote service."));
								BytesArray data = sendRequestWithoutData(Request.RequestType.ASK_FOR_DATA);
								if (data.getSize() > 0) {
									tcpForwarder.writeBytesArray(data);
									milliBeforeNextPolling.set(minSleepTime);
								} else {
									if (milliBeforeNextPolling.get() < maxSleepTime) {
										milliBeforeNextPolling.getAndAdd(incrementSleepTime);
									}
								}
							} catch (InvalidKeyException e) {
								processError("Encrypt/decrypt error.", e);
							} catch (BadPaddingException e) {
								processError("Encrypt/decrypt error.", e);
							} catch (IllegalBlockSizeException e) {
								processError("Encrypt/decrypt error.", e);
							} catch (IllegalStateException e) {
								processError("Encrypt/decrypt error.", e);
							} catch (ClassNotFoundException e) {
								processError("Impossible to deserialize request.", e);
							} catch (IOException e) {
								processError("Problem with IO.", e);
							}	
						}
					}
				}
			}
		}
	}
	
	
	public void setServerPort(Integer serverPort) {
		this.serverPort = serverPort;
	}
	
	public void setServerUrl(String serverUrl) {
		this.serverUrl = serverUrl;
	}
	
	public void setProxyEnabled(Boolean proxyEnabled) {
		this.proxyEnabled = proxyEnabled;
	}
	
	public void setProxyPort(Integer proxyPort) {
		this.proxyPort = proxyPort;
	}
	
	public void setProxyUrl(String proxyUrl) {
		this.proxyUrl = proxyUrl;
	}
	
	public void setMaxPollerSleepTime(int maxPollerSleepTime) {
		this.maxPollerSleepTime = maxPollerSleepTime;
	}
	
	public void setMinPollerSleepTime(int minPollerSleepTime) {
		this.minPollerSleepTime = minPollerSleepTime;
	}
	
	public void setIncrementPollerSleepTime(int incrementPollerSleepTime) {
		this.incrementPollerSleepTime = incrementPollerSleepTime;
	}
	
	public void setServiceInfo(ServiceInfo serviceInfo) {
		this.serviceInfo = serviceInfo;
	}
	
	public String prefixMessageWithService(Object message) {
		if (serviceInfo == null) {
			return message.toString();
		} else {
			return "[Service " + serviceInfo.getServiceName() + "] - " + message.toString();  
		}
	}
	
	@Override
	public Object clone() throws CloneNotSupportedException {
		return super.clone();
	}
}
