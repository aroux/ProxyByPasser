package com.proxy.bypasser.http;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.apache.http.HttpClientConnection;
import org.apache.http.HttpConnection;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ClientConnectionRequest;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.conn.ManagedClientConnection;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.entity.SerializableEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.InitializingBean;

import com.proxy.bypasser.data.BytesArray;
import com.proxy.bypasser.data.Request;
import com.proxy.bypasser.data.Response;
import com.proxy.bypasser.io.exc.FatalIOException;
import com.proxy.bypasser.services.ServiceInfo;
import com.proxy.bypasser.tcp.TcpForwarder;

public class SecureHttpClient extends SecureHttpPeer implements Cloneable, Runnable {
	
	private HttpClient httpClient;
	//private ManagedClientConnection con;
	private HttpContext httpContext;
	
	private String serverUrl;
	private Integer serverPort; 
		
	private boolean running;
	private boolean doNotReadData;
	
	private Boolean proxyEnabled;
	private String proxyUrl;
	private Integer proxyPort;
	
	private int maxPollerSleepTime;
	private int minPollerSleepTime;
	private int incrementPollerSleepTime;
	
	private HttpDataPoller poller;
	
	protected Logger logger = Logger.getLogger(SecureHttpClient.class);
	
	protected ServiceInfo serviceInfo;
	
	public SecureHttpClient() throws NoSuchAlgorithmException, NoSuchPaddingException, FileNotFoundException, IOException, ClassNotFoundException  {
		super();
	}
	
	public void init() throws Exception {
		Scheme http = new Scheme("http", serverPort, PlainSocketFactory.getSocketFactory());
		SchemeRegistry sr = new SchemeRegistry();
		sr.register(http);
		ClientConnectionManager connMrg = new SingleClientConnManager(sr);
		httpClient = new DefaultHttpClient(connMrg);
		if (proxyEnabled) {
			initProxyConfiguration();
		}
		httpContext = new BasicHttpContext();
		
		tcpForwarder = new TcpForwarder(serviceInfo.getSecureClientPort(), this);
	}
	
	private void initProxyConfiguration() {
		HttpHost proxy = new HttpHost(proxyUrl, proxyPort);
		httpClient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
	}
	
//	private void initConnectionWithServer() throws ConnectionPoolTimeoutException, InterruptedException {
//		Scheme http = new Scheme("http", serverPort, PlainSocketFactory.getSocketFactory());
//		SchemeRegistry sr = new SchemeRegistry();
//		sr.register(http);
//		ClientConnectionManager connMrg = new SingleClientConnManager(sr);
//		
//		// Request new connection. This can be a long process
//		ClientConnectionRequest connRequest = connMrg.requestConnection(
//		        new HttpRoute(new HttpHost(serverUrl, serverPort)), null);
//
//		// Wait for connection up to 10 sec
//		con = connRequest.getConnection(10, TimeUnit.SECONDS);
//	}
//	
	
	@Override
	public void run()  {
		try {
			init();
		} catch (Exception e1) {
			logger.fatal(prefixMessageWithService("Impossible to init SecureHttpClient. Exiting."));
			return;
		}
		running = true;
		logger.info(prefixMessageWithService("Running secure http client for service " + serviceInfo.getUrl() + ":" + serviceInfo.getPort() //
				+ ". Listening on port " + serviceInfo.getSecureClientPort() + "."));
		while (running) {
			try {
				tcpForwarder.waitForNextIncomingConnection();
				doNotReadData = false;
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
							if (!doNotReadData) {
								if (poller != null) poller.interruptPolling();
								BytesArray decryptedData = sendForwardRequest(data);
								if (decryptedData.getSize() > 0) {
									tcpForwarder.writeBytesArray(decryptedData);
								}
							}
						}
					}
				}
				poller.shutdown();
				poller = null;
			} catch (InvalidKeyException e) {
				logger.fatal(prefixMessageWithService("Encrypt/decrypt error."), e);
				closeCurrentConnection();
			} catch (BadPaddingException e) {
				logger.fatal(prefixMessageWithService("Encrypt/decrypt error."), e);
				closeCurrentConnection();
			} catch (IllegalBlockSizeException e) {
				logger.fatal(prefixMessageWithService("Encrypt/decrypt error."), e);
				closeCurrentConnection();
			} catch (IllegalStateException e) {
				logger.fatal(prefixMessageWithService("Encrypt/decrypt error."), e);
				closeCurrentConnection();
			} catch (ClassNotFoundException e) {
				logger.fatal(prefixMessageWithService("Impossible to deserialize request."), e);
				closeCurrentConnection();
			} catch (FatalIOException e) {
				logger.fatal(prefixMessageWithService("Fatal IO Exception. Shutdown all."), e);
				tcpForwarder.shutdown();
				running = false;
			} catch (IOException e) {
				logger.fatal(prefixMessageWithService("Problem with IO."), e);
				closeCurrentConnection();
			}
//			} catch (InterruptedException e) {
//				fatal("Problem with IO.", e);
//				closeCurrentConnection();
//			}	
		}
	}
	
	private void closeCurrentConnection() {
		doNotReadData = true;
		tcpForwarder.closeCurrentStreams();
//		if (con != null) {
//			try {
//				con.abortConnection();
//			} catch (IOException e) {
//				// Do nothing
//			}
//		}
	}
	
	private BytesArray sendForwardRequest(BytesArray data) throws InvalidKeyException, BadPaddingException, IllegalBlockSizeException, IllegalStateException, IOException, ClassNotFoundException {
		HttpPost httpPost = new HttpPost("http://" + serverUrl + ":" + serverPort);
		Request req = new Request(Request.RequestType.FORWARD, new BytesArray(data.getData(), data.getSize()), serviceInfo.getUrl(), serviceInfo.getPort());
		httpPost.setEntity(genEncryptedEntityFromObject(req));
		logger.info(prefixMessageWithService("Forwarding " + req.getData().getSize() + " bytes to secure http server"));
		HttpResponse response = httpClient.execute(httpPost, httpContext);
		return getDataFromResponse(response);
	}
	
	private BytesArray sendRequestWithoutData(Request.RequestType type) throws IOException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException, IllegalStateException, ClassNotFoundException {
		HttpPost httpPost = new HttpPost("http://" + serverUrl + ":" + serverPort);
		Request req = new Request(type, new BytesArray(null, 0), serviceInfo.getUrl(), serviceInfo.getPort());
		httpPost.setEntity(genEncryptedEntityFromObject(req));
		logger.info(prefixMessageWithService("Sending 'ASK_FOR_DATA' reques to secure http server"));
		HttpResponse response = httpClient.execute(httpPost, httpContext);
		return getDataFromResponse(response);
	}
	
	private BytesArray getDataFromResponse(HttpResponse response) throws IOException, IllegalStateException, ClassNotFoundException {
		HttpEntity entity = response.getEntity();
		Response internalResponse = readResponseFromEntity(entity);
		logger.info(prefixMessageWithService("Received " + internalResponse.getData().getSize() + " bytes from secure http server."));
		return internalResponse.getData();
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
			closeCurrentConnection();
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
					synchronized (SecureHttpClient.this) {
						interrupted = false;
					}
					logger.debug(prefixMessageWithService("Sleep for " + milliBeforeNextPolling.get() + " milliseconds."));
					Thread.sleep(milliBeforeNextPolling.get());
				} catch (InterruptedException e) {
					logger.debug(prefixMessageWithService("Sleep interrupted."));
				}
				
				synchronized(SecureHttpClient.this) {
					if (!interrupted) {
						try {
							logger.debug(prefixMessageWithService("Ask for data to remote service."));
							BytesArray data = sendRequestWithoutData(Request.RequestType.ASK_FOR_DATA);
							if (data.getSize() > 0) {
								tcpForwarder.writeBytesArray(data);
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
