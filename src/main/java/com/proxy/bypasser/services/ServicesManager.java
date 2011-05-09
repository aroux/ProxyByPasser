package com.proxy.bypasser.services;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

public class ServicesManager implements InitializingBean {
	
	Logger logger = Logger.getLogger(ServicesManager.class);

	private String pathToServicesProperties;
	
	private List<ServiceInfo> services;
	
	public ServicesManager() {
		services = new ArrayList<ServiceInfo>();
	}
	
	@Override
	public void afterPropertiesSet() throws Exception {
		URL directoryUrl = this.getClass().getClassLoader().getResource(pathToServicesProperties);
		String directoryPath = directoryUrl.getFile();
		File directory = new File(directoryPath);
		
		if (directory.isDirectory()) {
			for (File file : directory.listFiles()) {
				logger.info("Parsing service file '" + file.getName() + "'");
				Properties properties = PropertiesLoaderUtils.loadProperties(new FileSystemResource(file));
				
				String serviceUrl = properties.getProperty("service.url");
				if (serviceUrl == null) {
					logger.error("service.url is not defined in service file '" + file.getName() + "'. Skipping service.");
					continue;
				}
				
				String servicePortStr = properties.getProperty("service.port");
				Integer servicePort = null;
				if (servicePortStr == null) {
					logger.error("service.port is not defined in service file '" + file.getName() + "'. Skipping service.");
					continue;
				} else {
					try {
						servicePort = Integer.parseInt(servicePortStr);
					} catch (NumberFormatException e) {
						logger.error("service.port defined in service file '" + file.getName() + "' must be a number. Skipping service.");
						continue;
					}
				}
				
				String clientPortStr = properties.getProperty("secure.client.listening.port");
				Integer clientPort = null;
				if (clientPortStr == null) {
					logger.error("secure.client.listening.port is not defined in service file '" + file.getName() + "'. Skipping service.");
					continue;
				} else {
					try {
						clientPort = Integer.parseInt(clientPortStr);
					} catch (NumberFormatException e) {
						logger.error("secure.client.listening.port defined in service file '" + file.getName() + "' must be a number. Skipping service.");
						continue;
					}
				}
				
				ServiceInfo service = new ServiceInfo(file.getName().split("\\.")[0], serviceUrl, servicePort, clientPort);
				services.add(service);
			}
		} else {
			logger.error("Path to services properties does not point to a directory. No service will be available.");
		}
	}
	
	public void setPathToServicesProperties(String pathToServicesProperties) {
		this.pathToServicesProperties = pathToServicesProperties;
	}
	
	public List<ServiceInfo> getServices() {
		return services;
	}
}
