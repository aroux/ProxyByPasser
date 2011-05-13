package com.proxy.bypasser.data;

import java.io.Serializable;

import com.proxy.bypasser.services.ServiceInfo;


public class Request implements Serializable {

	private static final long serialVersionUID = -2193091130641718696L;
	
	private final RequestType requestType;
	
	private final BytesArray data;
	
	private final String serviceName;
	
	private final String urlService;
	
	private final Integer portService;
	
	private final String serviceInstanceId;
	
	public Request(RequestType requestType, BytesArray data, ServiceInfo serviceInfo, String serviceInstanceId) {
		super();
		this.requestType = requestType;
		this.data = data;
		this.urlService = serviceInfo.getUrl();
		this.portService = serviceInfo.getPort();
		this.serviceName = serviceInfo.getServiceName();
		this.serviceInstanceId = serviceInstanceId;
	}

	public BytesArray getData() {
		return data;
	}
	
	public RequestType getRequestType() {
		return requestType;
	}
	
	public String getServiceName() {
		return serviceName;
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
	
	public String getServiceInstanceId() {
		return serviceInstanceId;
	}
}
