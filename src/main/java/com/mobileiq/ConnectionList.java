package com.mobileiq;

import java.io.Serializable;

import org.apache.catalina.connector.Request;

public class ConnectionList implements Serializable {
	
	transient Request[] connectionList = null;
	
	ConnectionList(Request[] connectionList){
		this.connectionList = connectionList;
	}
	
	public Request[] get(){
		return connectionList;
	}
}