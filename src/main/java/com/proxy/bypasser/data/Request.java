package com.proxy.bypasser.data;
import java.io.Serializable;


public class Request implements Serializable {

	private static final long serialVersionUID = -2193091130641718696L;
	
	private final RequestType requestType;
	
	private final BytesArray data;
	
	private final String urlService;
	
	private final Integer portService;
	
	public Request(RequestType requestType, BytesArray data, String urlService, Integer portService) {
		super();
		this.requestType = requestType;
		this.data = data;
		this.urlService = urlService;
		this.portService = portService;
	}

	public BytesArray getData() {
		return data;
	}
	
	public RequestType getRequestType() {
		return requestType;
	}
	
	public Integer getPortService() {
		return portService;
	}
	
	public String getUrlService() {
		return urlService;
	}
	
	public enum RequestType implements Serializable {
		FORWARD(0), ASK_FOR_DATA(1), REINIT_SERVICE_STREAM(2);
		
		private final int code;

		private RequestType(int code) {
			this.code = code;
		}
		
		public int getCode() {
			return code;
		}
	}
}
