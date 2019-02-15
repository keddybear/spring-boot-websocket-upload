package com.exmaple.webserver.websocket;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/*
 * Author: Dikai Xiong
 * Date: 2/10/2019
 */

@Configuration
@EnableWebSocket
public class WebSocketConfiguration implements WebSocketConfigurer {

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(uploadHandler(), "/upload");
	}
	
	@Bean
	public UploadHandler uploadHandler() {
		return new UploadHandler();
	}
	
}
