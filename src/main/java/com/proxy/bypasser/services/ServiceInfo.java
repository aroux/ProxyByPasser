package com.proxy.bypasser.services;

public class ServiceInfo {
	
	private String serviceName;

	private String url;
	
	private Integer port;
	
	private Integer secureClientPort;
	
	public ServiceInfo(String serviceName, String url, Integer port, Integer secureClientPort) {
		super();
		this.serviceName = serviceName;
		this.url = url;
		this.port = port;
		this.secureClientPort = secureClientPort;
	}
	
	public String getServiceName() {
		return serviceName;
	}
	
	public Integer getPort() {
		return port;
	}
	
	public String getUrl() {
		return url;
	}
	
	public Integer getSecureClientPort() {
		return secureClientPort;
	}
}
