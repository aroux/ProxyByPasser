package com.proxy.bypasser.http;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.entity.SerializableEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.log4j.Logger;

import com.proxy.bypasser.data.BytesArray;
import com.proxy.bypasser.data.Request;
import com.proxy.bypasser.data.Response;
import com.proxy.bypasser.io.exc.FatalIOException;
import com.proxy.bypasser.tcp.TcpForwarder;

public class SecureHttpClient extends SecureHttpPeer {
	
	Logger logger = Logger.getLogger(SecureHttpClient.class);
	
	private HttpClient httpClient;
	
	private String serverUrl;
	private Integer serverPort; 
	
	private Integer servicePort;
	private String serviceUrl;
		
	private boolean running;
	private boolean doNotReadData;
	
	private Boolean proxyEnabled = false;
	private String proxyUrl = "localhost";
	private Integer proxyPort = 8080;
	
	private int maxPollerSleepTime;
	private int minPollerSleepTime;
	private int incrementPollerSleepTime;
	
	private HttpDataPoller poller;
	
	public SecureHttpClient() throws NoSuchAlgorithmException, NoSuchPaddingException, FileNotFoundException, IOException, ClassNotFoundException  {
		super();
		
		httpClient = new DefaultHttpClient();
		if (proxyEnabled) {
			initProxyConfiguration();
		}
		
		tcpForwarder = new TcpForwarder();
	}
	
	private void initProxyConfiguration() {
		HttpHost proxy = new HttpHost(proxyUrl, proxyPort);
		httpClient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
	}
	
	public void run()  {
		running = true;
		while (running) {
			try {
				tcpForwarder.waitForNextIncomingConnection();
				doNotReadData = false;
				while (tcpForwarder.canRead()) {
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
					if (poller == null) {
						poller = new HttpDataPoller(maxPollerSleepTime, minPollerSleepTime, incrementPollerSleepTime);
						poller.start();
					}
				}
				poller.shutdown();
				poller = null;
			} catch (InvalidKeyException e) {
				logger.fatal("Encrypt/decrypt error.", e);
				closeCurrentConnection();
			} catch (BadPaddingException e) {
				logger.fatal("Encrypt/decrypt error.", e);
				closeCurrentConnection();
			} catch (IllegalBlockSizeException e) {
				logger.fatal("Encrypt/decrypt error.", e);
				closeCurrentConnection();
			} catch (IllegalStateException e) {
				logger.fatal("Encrypt/decrypt error.", e);
				closeCurrentConnection();
			} catch (ClassNotFoundException e) {
				logger.fatal("Impossible to deserialize request.", e);
				closeCurrentConnection();
			} catch (FatalIOException e) {
				logger.fatal("Fatal IO Exception. Shutdown all.", e);
				tcpForwarder.shutdown();
				running = false;
			} catch (IOException e) {
				logger.fatal("Problem with IO.", e);
				closeCurrentConnection();
			}	
		}
	}
	
	private void closeCurrentConnection() {
		doNotReadData = true;
		tcpForwarder.closeCurrentStreams();
	}
	
	private BytesArray sendForwardRequest(BytesArray data) throws InvalidKeyException, BadPaddingException, IllegalBlockSizeException, IllegalStateException, IOException, ClassNotFoundException {
		HttpPost httpPost = new HttpPost("http://" + serverUrl + ":" + serverPort);
		Request req = new Request(Request.RequestType.FORWARD, new BytesArray(data.getData(), data.getSize()), serviceUrl, servicePort);
		httpPost.setEntity(genEncryptedEntityFromObject(req));
		logger.info("Forwarding " + req.getData().getSize() + " bytes to secure http server");
		HttpResponse response = httpClient.execute(httpPost);
		return getDataFromResponse(response);
	}
	
	private BytesArray sendAskForDataRequest() throws IOException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException, IllegalStateException, ClassNotFoundException {
		HttpPost httpPost = new HttpPost("http://" + serverUrl + ":" + serverPort);
		Request req = new Request(Request.RequestType.ASK_FOR_DATA, new BytesArray(null, 0), serviceUrl, servicePort);
		httpPost.setEntity(genEncryptedEntityFromObject(req));
		logger.info("Sending 'ASK_FOR_DATA' reques to secure http server");
		HttpResponse response = httpClient.execute(httpPost);
		return getDataFromResponse(response);
	}
	
	private BytesArray getDataFromResponse(HttpResponse response) throws IOException, IllegalStateException, ClassNotFoundException {
		HttpEntity entity = response.getEntity();
		Response internalResponse = readResponseFromEntity(entity);
		logger.info("Received " + internalResponse.getData().getSize() + " bytes from secure http server.");
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
			logger.fatal(errorText, e);
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
			
			logger.info("Start HttpDataPoller.");
			
			while (running) {
				try {
					synchronized (SecureHttpClient.this) {
						interrupted = false;
					}
					logger.debug("Sleep for " + milliBeforeNextPolling.get() + " milliseconds.");
					Thread.sleep(milliBeforeNextPolling.get());
				} catch (InterruptedException e) {
					logger.debug("Sleep interrupted.");
				}
				
				synchronized(SecureHttpClient.this) {
					if (!interrupted) {
						try {
							logger.debug("Ask for data to remote service.");
							BytesArray data = sendAskForDataRequest();
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
	
	public void setServicePort(Integer servicePort) {
		this.servicePort = servicePort;
	}
	
	public void setServiceUrl(String serviceUrl) {
		this.serviceUrl = serviceUrl;
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
}
