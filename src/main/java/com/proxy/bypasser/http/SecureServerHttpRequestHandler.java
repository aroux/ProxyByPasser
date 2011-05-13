package com.proxy.bypasser.http;
import java.io.IOException;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.log4j.Logger;

import com.proxy.bypasser.data.BytesArray;
import com.proxy.bypasser.data.Request;
import com.proxy.bypasser.data.Response;
import com.proxy.bypasser.services.ServiceInfo;
import com.proxy.bypasser.tcp.TcpForwarder;

public class SecureServerHttpRequestHandler implements HttpRequestHandler {
	
	Logger logger = Logger.getLogger(SecureServerHttpRequestHandler.class);
	
	private void processServiceIOError(String errorText, IOException e, HttpResponse response, 
			TcpForwarder tcpForwarder) {
		logger.fatal(errorText, e);
		response.addHeader("Content-Length", "0");
		if (tcpForwarder != null) {
			tcpForwarder.shutdown();
		}
	}
	
	private void processError(String errorText, Exception e, HttpResponse response) {
		logger.fatal(errorText, e);
		response.addHeader("Content-Length", "0");
	}
	
	public void handle(
            HttpRequest request, 
            HttpResponse response, 
            HttpContext context) throws HttpException {
    	
		@SuppressWarnings("unchecked")
		Map<String, TcpForwarder> tcpForwarders = (Map<String, TcpForwarder>) context.getAttribute("tcpForwarders");
		SecureHttpServer secureHttpServer = (SecureHttpServer) context.getAttribute("secureHttpServer");
		TcpForwarder tcpForwarder = null;
		try {
	    	HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
			
			Request internalRequest = secureHttpServer.readRequestFromEntity(entity);
			
			synchronized (tcpForwarders) {
				tcpForwarder = tcpForwarders.get(internalRequest.getServiceInstanceId());
				if (tcpForwarder == null) {
					tcpForwarder = new TcpForwarder();
					tcpForwarder.setPrefixService("[Service " + internalRequest.getServiceName() + "] - ");
					tcpForwarder.setServiceInstanceId(internalRequest.getServiceInstanceId());
					tcpForwarders.put(internalRequest.getServiceInstanceId(), tcpForwarder);
				}
			}
			
			logger.info(tcpForwarder.prefixMessageWithService("Received " + internalRequest.getRequestType().name() + " request of " // 
					+ internalRequest.getData().getSize() + " bytes from " //
					+ ((HttpRequest) request).getFirstHeader("host") + " for service " + internalRequest.getUrlService() //
					+ ":" + internalRequest.getPortService()));
			
			Response internalResponse;
			if (internalRequest.getRequestType().equals(Request.RequestType.REINIT_SERVICE_STREAM)) {
				tcpForwarder.closeCurrentStreams();
				tcpForwarder.openNewStreams(internalRequest.getUrlService(), internalRequest.getPortService());
				internalResponse = new Response(Response.ResponseType.OK, new BytesArray(null, 0));
			} else {
				if (internalRequest.getRequestType().equals(Request.RequestType.FORWARD)) {
					tcpForwarder.writeBytesArray(internalRequest.getData());
				}
				
				BytesArray dataReadFromService = tcpForwarder.readBytesArray(false);
				internalResponse = new Response(Response.ResponseType.OK, dataReadFromService);
			}
			ByteArrayEntity encryptedEntity = secureHttpServer.genEncryptedEntityFromObject(internalResponse);
			response.setHeader("Content-length", String.valueOf(encryptedEntity.getContentLength()));
			response.setEntity(encryptedEntity);
	        logger.debug(tcpForwarder.prefixMessageWithService("Responding back to client " + encryptedEntity.getContentLength() + " bytes."));
		} catch (IllegalStateException e) {
			processError("Encrypt/decrypt error.", e, response);
		} catch (ClassNotFoundException e) {
			processError("Impossible to deserialize request.", e, response);
		} catch (IOException e) {
			processServiceIOError("Service socket error.", e, response, tcpForwarder);
		}
    }
}