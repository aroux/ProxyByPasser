package com.proxy.bypasser.http;
import java.io.IOException;
import java.security.InvalidKeyException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.SerializableEntity;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.log4j.Logger;

import com.proxy.bypasser.crypt.PrivacyMaker;
import com.proxy.bypasser.data.BytesArray;
import com.proxy.bypasser.data.Request;
import com.proxy.bypasser.data.Response;
import com.proxy.bypasser.tcp.TcpForwarder;

public class SecureServerHttpRequestHandler implements HttpRequestHandler {
	
	Logger logger = Logger.getLogger(SecureServerHttpRequestHandler.class);
	
	private void processServiceIOError(String errorText, IOException e, HttpResponse response, SecureHttpServer secureHttpServer) {
		logger.fatal(errorText, e);
		response.addHeader("Content-Length", "0");
		secureHttpServer.closeServiceStreams();
	}
	
	private void processError(String errorText, Exception e, HttpResponse response) {
		logger.fatal(errorText, e);
		response.addHeader("Content-Length", "0");
	}
	
	public void handle(
            HttpRequest request, 
            HttpResponse response, 
            HttpContext context) throws HttpException {
    	
		TcpForwarder tcpForwarder = (TcpForwarder) context.getAttribute("tcpForwarder");
		SecureHttpServer secureHttpServer = (SecureHttpServer) context.getAttribute("secureHttpServer");
		try {
	    	HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
			
			Request internalRequest = secureHttpServer.readRequestFromEntity(entity);
			logger.info("Received " + internalRequest.getRequestType().name() + " request of " // 
					+ internalRequest.getData().getSize() + " bytes from " //
					+ ((HttpRequest) request).getFirstHeader("host") + " for service " + internalRequest.getUrlService() //
					+ ":" + internalRequest.getPortService());
			
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
	        logger.debug("Responding back to client " + encryptedEntity.getContentLength() + " bytes.");
		} catch (IllegalStateException e) {
			processError("Encrypt/decrypt error.", e, response);
		} catch (ClassNotFoundException e) {
			processError("Impossible to deserialize request.", e, response);
		} catch (IOException e) {
			processServiceIOError("Service socket error.", e, response, secureHttpServer);
		}
		
		
    }
	
	
}