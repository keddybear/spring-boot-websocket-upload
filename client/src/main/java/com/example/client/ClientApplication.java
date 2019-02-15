package com.example.client;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;

import com.example.client.websocket.ClientUploadSocket;

/*
 * Author: Dikai Xiong
 * Date: 2/10/2019
 */

/*
 * ClientApplication:
 * 
 * 1. Establish a websocket connection to ws://localhost:8080/upload
 * 2. Once server is connected and ready, start uploading files in the "assets" folder to the server.
 * 3. Upload is handled by ClientUploadSocket, which uploads each file in small chunks (8KB).
 */
public class ClientApplication {

	public static void main(String[] args) {
		String destUri = "ws://localhost:8080/upload";
		if (args.length > 0) {
			destUri = args[0];
		}
		
		WebSocketClient client = new WebSocketClient();
		ClientUploadSocket socket = new ClientUploadSocket();
		
		try {
			client.start();
			URI echoUri = new URI(destUri);
			ClientUpgradeRequest request = new ClientUpgradeRequest();
			client.connect(socket, echoUri, request);
			System.out.printf("Connecting to : %s%n", echoUri);
			System.out.flush();
			
			// Wait for connection to close or close after 300 seconds
			socket.awaitClose(300, TimeUnit.SECONDS);
		} catch (Throwable t) {
			t.printStackTrace();
		} finally {
			try {
				client.stop();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
