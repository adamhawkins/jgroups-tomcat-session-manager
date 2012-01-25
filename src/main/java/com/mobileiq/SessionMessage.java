package com.mobileiq;

import java.io.Serializable;

import org.jgroups.Address;

public class SessionMessage implements Serializable {
	
	public enum Event {
		SESSION_CREATED,
		SESSION_EXPIRED_WONOTIFY,
		SESSION_EXPIRED_WNOTIFY,
		SESSION_ACCESSED,
		GET_ALL_SESSIONS,
		ATTRIBUTE_ADDED,
		ATTRIBUTE_REMOVED_WONOTIFY,
		ATTRIBUTE_REMOVED_WNOTIFY,
		SET_USER_PRINCIPAL,
		REMOVE_SESSION_NOTE,
		SET_SESSION_NOTE
	}
	
	private Event event;
	private byte[] mSession;
	private String mSessionID;
	private String mAttributeName;
	private Object mAttributeValue;
	private SerializablePrincipal mPrincipal;
	private Address mSrc;

	public SessionMessage(Event event, byte[] session, String sessionID,String attrName, Object attrValue, SerializablePrincipal principal) {
		this.event = event;
		this.mSession = session;
		this.mSessionID = sessionID;
		this.mAttributeName = attrName;
		this.mAttributeValue = attrValue;
		this.mPrincipal = principal;
	}

	public Event getEventType() {
		return this.event;
	}

	public byte[] getSession() {
		return this.mSession;
	}

	public String getSessionID() {
		return this.mSessionID;
	}

	public String getAttributeName() {
		return this.mAttributeName;
	}

	public Object getAttributeValue() {
		return this.mAttributeValue;
	}

	public SerializablePrincipal getPrincipal() {
		return this.mPrincipal;
	}

	public String getEventTypeString() {
		return this.event.name();
	}

	public Address getAddress() {
		return this.mSrc;
	}

	public void setAddress(Address src) {
		this.mSrc = src;
	}
}