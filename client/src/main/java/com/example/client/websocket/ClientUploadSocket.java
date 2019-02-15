package com.example.client.websocket;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.json.JSONObject;

/*
 * Author: Dikai Xiong
 * Date: 2/10/2019
 */

/*
 * ClientUploadSocket:
 * 
 * 1. Once connection is established:
 * 		- It fetches information about the files in the "assets" folder.
 * 		- It sends an initialization message to the server, including username, a security token, files' names and sizes,
 * 		  and a boundary, which is a String indicating the end of a file.
 * 		- All sent messages are binary.
 * 2. The whole process is async. It uses CountDownLatch for the main thread to wait for its execution.
 * 3. If there's no files to upload, close the connection.
 * 4. It creates a new Thread to upload each file, and handles server response in onMessage().
 * 5. After the last file is sccessfully uploaded, it send a termination message to the server.
 * 6. Print out a pretty progress bar for each upload.
 * 
 * NOTE:
 * 
 * Ideally, client and server should exchange some private keys to encrypt the upload, but it's not done here.
 */
@WebSocket(maxBinaryMessageSize = 64 * 1024)
public class ClientUploadSocket {
	
	private final CountDownLatch latch;
	private Session session;
	private String assetFolder = "./src/main/assets/";
	private String[] filesToUpload;
	private String[] fileSizes;
	private int filePointer; // Indicate which file to upload
	private int progressBars; // For printing pretty progress
	private int barSize = 50; // How many bars to repsent 100%
	private String boundary;
	
	public ClientUploadSocket() {
		this.latch = new CountDownLatch(1);
	}
	
	public boolean awaitClose(int duration, TimeUnit unit) throws InterruptedException {
		return this.latch.await(duration, unit);
	}
	
	@OnWebSocketClose
	public void onClose(int statusCode, String reason) {
		System.out.printf("Connection closed: %d - %s%n", statusCode, reason);
		System.out.flush();
		this.latch.countDown();
	}
	
	@OnWebSocketConnect
	public void onConnect(Session session) {
		this.session = session;
		// Get files to upload
		getFileNamesFromAssets();
		if (this.filesToUpload == null) {
			System.out.println("No files to upload in assets folder");
			session.close(StatusCode.NORMAL, "done");
			return;
		}
		// Initialize file pointer & progress bars
		this.filePointer = 0;
		this.progressBars = 0;
		
		// Send an initialization message
		sendInitMessage();
	}
	
	@OnWebSocketMessage
	public void onMessage(String msg) {
		String[] message = msg.split("\\|");
		switch (message[0]) {
			case "ready": {
				// Upload first file
				this.filePointer = 0;
				// Clear progress bars
				this.progressBars = 0;
				
				if (this.filePointer >= this.filesToUpload.length) {
					sendTerminationMessage();
				} else {
					// Prepare progress bar
					prepareProgressBar();
					// Create a new Thread for upload
					Runnable asyncUpload = new UploadRunnable(this.filesToUpload[this.filePointer], this.session, this.boundary);
					Thread thread = new Thread(asyncUpload);
					thread.start();
				}
				break;
			}
			case "next": {
				// When server is ready to upload the next file, create a new Thread
				// Or send a termination message if there's no file to upload
				
				// Increase file pointer by 1
				this.filePointer += 1;
				
				if (this.filePointer > 0) {
					// Finish progress bars
					System.out.print(new String(new char[this.barSize - this.progressBars]).replace("\0", "#"));
					System.out.println(" 100%");
					System.out.println("\n");
					
					// Clear progress bars
					this.progressBars = 0;
				}
				
				if (this.filePointer >= this.filesToUpload.length) {
					sendTerminationMessage();
				} else {
					// Prepare progress bar
					prepareProgressBar();
					// Create a new Thread for upload
					Runnable asyncUpload = new UploadRunnable(this.filesToUpload[this.filePointer], this.session, this.boundary);
					Thread thread = new Thread(asyncUpload);
					thread.start();
				}
				break;
			}
			case "progress": {
				long current = Long.parseLong(this.fileSizes[this.filePointer]);
				long uploaded = Long.parseLong(message[1]);
				
				int barPercent = 100/this.barSize;
				int bars = (int) ((uploaded * 100)/current);
				
				int barsToPrint = bars/barPercent - this.progressBars;
				
				System.out.print(new String(new char[barsToPrint]).replace("\0", "#"));
				
				this.progressBars += barsToPrint;

				System.out.flush();
				break;
			}
			default: {
				System.out.printf("Server: %s%n", msg);
				System.out.flush();
				this.session.close(StatusCode.NORMAL, "done");
				break;
			}
		}
	}
	
	private void getFileNamesFromAssets() {
		File assets = new File(assetFolder);
		
		if (assets.exists()) {
			File[] files = assets.listFiles();
			this.filesToUpload = new String[files.length];
			this.fileSizes = new String[files.length];
			
			for (int i = 0; i < files.length; i += 1) {
				if (files[i].isFile()) {
					this.filesToUpload[i] = files[i].getName();
					this.fileSizes[i] = String.valueOf(files[i].length());
				}
			}
		} else {
			this.filesToUpload = null;
			this.fileSizes = null;
		}
	}
	
	private void sendInitMessage() {
		// Create an initialization message
		JSONObject init = new JSONObject();
		
		// username - Server will upload files in a local folder named by the username
		// token - A security token currently not used
		// filenames - An array of file names to be uploaded
		// sizes - An array of respective file sizes in String
		// command - It tells the server to prepare for upload
		// boundary - It tells the server the last binary packet for the current file has been sent
		String username = "username";
		String key = "randomKey";
		String filenames = String.join("|", this.filesToUpload);
		String sizes = String.join("|", this.fileSizes);
		String command = "init";
		byte[] array = new byte[7];
		new Random().nextBytes(array);
		this.boundary = new String (array, Charset.forName("UTF-8"));
		
		// Put into message
		init.put("username", username);
		init.put("token", key);
		init.put("filenames", filenames);
		init.put("sizes", sizes);
		init.put("command", command);
		init.put("boundary", this.boundary);
		
		try {
			// Create a future result to wait for response
			Future<Void> result;
			// Send the init message
			ByteBuffer sendData = ByteBuffer.wrap(init.toString().getBytes("UTF-8"));
			result = session.getRemote().sendBytesByFuture(sendData);
			// Wait for send success or failure (similar to async await in Javascript)
			result.get();
		} catch (Throwable t) {
			t.printStackTrace();
			this.session.close(StatusCode.NORMAL, "done");
		}
	}
	
	private void sendTerminationMessage() {
		// Create an initialization message
		JSONObject termination = new JSONObject();
		termination.put("command", "exit");
		
		try {
			// Create a future result to wait for response
			Future<Void> result;
			// Send the init message
			ByteBuffer sendData = ByteBuffer.wrap(termination.toString().getBytes("UTF-8"));
			result = session.getRemote().sendBytesByFuture(sendData);
			// Wait for send success or failure (similar to async await in Javascript)
			result.get();
		} catch (Throwable t) {
			t.printStackTrace();
			this.session.close(StatusCode.NORMAL, "done");
		}
	}
	
	private void prepareProgressBar() {
		System.out.println("\"" + this.filesToUpload[this.filePointer] + "\"");
		// print out a progress boundary (Eclipse does not support overwriting the same line)
		System.out.println("|" + new String(new char[this.barSize]).replace("\0", " ") + "| " + getFileSizeWithUnit(this.fileSizes[this.filePointer]));
		// This is where the progress bar will be printed
		System.out.print(" ");
		System.out.flush();
	}
	
	private String getFileSizeWithUnit(String fileSize) {
		long size = Long.parseLong(fileSize);
		
		if(size <= 0) return "0";
		
		final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
		int digitGroups = (int) (Math.log10(size)/Math.log10(1024));
		
		return new DecimalFormat("#,##0.#").format(size/Math.pow(1024, digitGroups)) + " " + units[digitGroups];
	}
}
