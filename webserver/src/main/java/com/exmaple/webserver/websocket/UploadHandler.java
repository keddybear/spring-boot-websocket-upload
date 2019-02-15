package com.exmaple.webserver.websocket;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

import org.json.JSONObject;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

/*
 * Author: Dikai Xiong
 * Date: 2/11/2019
 */

/*
 * UploadState:
 * 
 * 1. IDLE: Wait for the initialization message.
 * 2. UPLOADING: Wait for binary file chunks.
 * 3. FINISHED: Wait for the termination message.
 */
enum UploadState {
	IDLE, UPLOADING, FINISHED;
}

/*
 * UploadHandler
 * 
 * 1. Handle client message depending on the server state.
 * 		- IDLE:
 * 			- Expect an initialization message.
 * 			- If there are files to upload and boundary is not null, create a destination folder to upload files.
 * 			- Reset uploadPointer, uploadPosition, lastUpdatePosition.
 * 			- Change internal state to UPLOADING.
 * 			- Send "ready" to client.
 * 			- If the initialization message is invalid, send "reject" to client.
 * 		- UPLOADING:
 * 			- Expect binary chunks for the current file from client.
 * 			- Send the number of uploaded bytes back to client for every 1M.
 * 			- Send "next" to client when reciving boundary.
 * 				- If there's no next file to upload, change state to FINISHED.
 * 				- NOTE: Maybe negotiate a termination token with the client?
 * 		- FINISHED:
 * 			- Expect a termination message.
 * 			- NOTE: Check integrity of the connection?
 * 			- Close connection, clean up and change state to IDLE.
 */
public class UploadHandler extends BinaryWebSocketHandler {
	
	private UploadState STATE = UploadState.IDLE;
	private String destDir = "./src/main/";
	private String[] filesToUpload;
	private String[] fileSizes;
	private int uploadPointer;
	private long uploadPosition;
	private long lastUpdatePosition;
	private long updateSize = 1024 * 1024;
	private String boundary;
	
	// afterConnectionEstablished

	@Override
    public void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        try {
        	if (this.STATE == UploadState.IDLE) {
        		handleIdle(session, message);
        	} else if (this.STATE == UploadState.UPLOADING) {
        		handleUploading(session, message);
        	} else if (this.STATE == UploadState.FINISHED) {
        		handleFinished(session, message);
        	}
        } catch (Throwable t) {
        	t.printStackTrace();
        }
    }
	
	// afterConnectionClosed
	
	/*
	 * handleIdle:
	 * 
	 * 1. Expect an initialization message.
	 * 2. If there are files to upload and boundary is not null, create a destination folder to upload files.
	 * 3. Reset uploadPointer, uploadPosition, lastUpdatePosition.
	 * 4. Change internal state to UPLOADING.
	 * 5. Send "ready" to client.
	 * 6. If the initialization message is invalid, send "reject" to client.
	 */
	private void handleIdle(WebSocketSession session, BinaryMessage message) {
		try {
			// Get payload data
	    	ByteBuffer sentData = message.getPayload();
	        String json = StandardCharsets.UTF_8.decode(sentData).toString();
	        JSONObject payload = new JSONObject(json);
	        
	        destDir = destDir + payload.getString("username") + File.separator;
	        String token = payload.getString("token");
	        String command = payload.getString("command");
	        this.filesToUpload = payload.getString("filenames").split("\\|");
	        this.fileSizes = payload.getString("sizes").split("\\|");
	        this.boundary = payload.getString("boundary");
	        
	        String res = "reject";
	        if (command.equals("init") && this.filesToUpload != null && this.filesToUpload.length > 0 && this.boundary != null) {
	        	// Initialization
	        	// Create destination folder if it does not exist
	        	File targetDir = new File(destDir);
	        	if (targetDir.exists() || targetDir.mkdirs()) {
	        		res = "ready";
	        		this.STATE = UploadState.UPLOADING;
	        		this.uploadPointer = 0;
	        		this.uploadPosition = 0;
	        		this.lastUpdatePosition = 0;
	        	}
	        }
	        TextMessage response = new TextMessage(res);
	    	session.sendMessage(response);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/*
	 * handleUploading:
	 * 
	 * 1. Expect binary chunks for the current file from client.
	 * 2. Send the number of uploaded bytes back to client for every 1M.
	 * 3. Send "next" to client when reciving boundary.
	 * 4. If there's no next file to upload, change state to FINISHED.
	 * 		- NOTE: Maybe negotiate a termination token with the client?
	 */
	private void handleUploading(WebSocketSession session, BinaryMessage message) {
		try {
			// Get file chunk
			ByteBuffer sentData = message.getPayload();
			// Check if chunk is boundary
    		if (Arrays.toString(sentData.array()).equals(Arrays.toString(this.boundary.getBytes()))) {
    			this.uploadPointer += 1;
    			this.uploadPosition = 0;
    			this.lastUpdatePosition = 0;
    			session.sendMessage(new TextMessage("next"));
    			if (this.uploadPointer >= this.filesToUpload.length) {
    				this.STATE = UploadState.FINISHED;
    			}
    		} else {
    			RandomAccessFile aFile = new RandomAccessFile(destDir + this.filesToUpload[this.uploadPointer], "rw");
    			FileChannel out = aFile.getChannel();
    			MappedByteBuffer outBuf = out.map(FileChannel.MapMode.READ_WRITE, this.uploadPosition, sentData.remaining());
	        	outBuf.put(sentData.array());
	        	this.uploadPosition += sentData.remaining();
	        	outBuf.clear();
	        	out.close();
	        	aFile.close();
	        	// For every 1M uploaded, send back a progress update
	        	if (this.uploadPosition - this.lastUpdatePosition >= this.updateSize) {
	        		session.sendMessage(new TextMessage("progress|" + this.uploadPosition));
	        		this.lastUpdatePosition = this.uploadPosition;
	        	}
    		}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/*
	 * handleFinished:
	 * 
	 * 1. Expect a termination message.
	 * 2. NOTE: Check integrity of the connection?
	 * 3. Close connection, clean up and change state to IDLE.
	 */
	private void handleFinished(WebSocketSession session, BinaryMessage message) {
		try {
			// Get payload data
	    	ByteBuffer sentData = message.getPayload();
	        String json = StandardCharsets.UTF_8.decode(sentData).toString();
	        JSONObject payload = new JSONObject(json);
	        
	        String command = payload.getString("command");
	        
	        // Check integrity
	        
	        if (command.equals("exit")) {
	        	// Clean up
	        }
	        
	        // Always close for this sample application
	        session.close(CloseStatus.NORMAL);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
}
