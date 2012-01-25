package com.mobileiq;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URL;

import org.apache.catalina.Session;
import org.apache.catalina.realm.GenericPrincipal;
import org.apache.catalina.session.ManagerBase;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.util.CustomObjectInputStream;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.jgroups.Address;
import org.jgroups.Channel;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.Receiver;
import org.jgroups.View;
import org.jgroups.blocks.MessageDispatcher;
import org.jgroups.conf.ConfiguratorFactory;
import org.jgroups.conf.ProtocolStackConfigurator;

import com.mobileiq.SessionMessage.Event;

public class JGroupsReplicationManager extends StandardManager {
	protected JChannel channel = null;

	protected ProtocolStackConfigurator protoConfig = null;

	protected boolean mChannelStarted = false;

	protected boolean mPrintToScreen = false;

	protected MessageDispatcher disp = null;

	protected boolean mManagerRunning = false;

	protected boolean deadlockDetection = true;

	protected boolean synchronousReplication = false;

	protected long replicationTimeout = 5000L;

	protected boolean mExpireSessionsOnShutdown = true;
	
	protected Receiver receiver;

    final Log log = LogFactory.getLog(JGroupsReplicationManager.class); // must not be static

	public void setProtocolStack(String path) {
		log.debug("Setting channel configuration to:" + path);
		
		URL url = this.getClass().getResource(path);
        
        log.info("Configuring jgroups session replication using " + url);

        try{
        	this.protoConfig = ConfiguratorFactory.getStackConfigurator(url);
        }catch(Exception e){
        	log.error("Configuring jgroups session replication", e);
        }
		
	}

	public boolean isManagerRunning() {
		return this.mManagerRunning;
	}

	public void setExpireSessionsOnShutdown(boolean expireSessionsOnShutdown) {
		this.mExpireSessionsOnShutdown = expireSessionsOnShutdown;
	}

	public boolean getExpireSessionsOnShutdown() {
		return this.mExpireSessionsOnShutdown;
	}

	public void setPrintToScreen(boolean printtoscreen) {
		log.debug("Setting screen debug to:" + printtoscreen);
		this.mPrintToScreen = printtoscreen;
	}

	public void setDeadlockDetection(boolean flag) {
		this.deadlockDetection = flag;
	}

	public void setSynchronousReplication(boolean flag) {
		this.synchronousReplication = flag;
	}

	public void setReplicationTimeout(long val) {
		this.replicationTimeout = val;
	}

	protected Session createSession(boolean notify, boolean setId) {
				
		if ((getMaxActiveSessions() >= 0) && (this.sessions.size() >= getMaxActiveSessions())) {
			throw new IllegalStateException(ManagerBase.sm.getString("standardManager.createSession.ise"));
		}

		Session session = null;

		/**
		 * synchronized (this.recycled) { int size = this.recycled.size(); int
		 * index = size; if (size > 0) { do { index--; session =
		 * (Session)this.recycled.get(index); this.recycled.remove(index); if
		 * (index <= 0) break; }while ((session instanceof ReplicatedSession));
		 * } }
		 **/

		if (session != null)
			session.setManager(this);
		else {
			session = new ReplicatedSession(this);
		}

		session.setNew(true);
		session.setValid(true);
		session.setCreationTime(System.currentTimeMillis());
		session.setMaxInactiveInterval(this.maxInactiveInterval);
		String sessionId = generateSessionId();
		String jvmRoute = getJvmRoute();

		if (jvmRoute != null) {
			sessionId = sessionId + '.' + jvmRoute;
		}

		if (setId)
			session.setId(sessionId);

		if (notify) {
			log.debug("Replicated Session created at "+ this.channel.getLocalAddress() + " with ID=" + session.getId());

			SessionMessage msg = new SessionMessage(Event.SESSION_CREATED, writeSession(session),session.getId(), null, null, null);

			sendSessionEvent(msg);
		}
		return session; 
	}

	@Override
	public Session createSession(String sessionId) {
		Session session = createSession(true, true);
		add(session);
		return session;
	}

	protected byte[] writeSession(Session session) {
		try {
			ByteArrayOutputStream session_data = new ByteArrayOutputStream();
			ObjectOutputStream session_out = new ObjectOutputStream(
					session_data);
			session_out.flush();
			session_out.close();
			((ReplicatedSession) session).writeObjectData(session_out);
			return session_data.toByteArray();
		} catch (Exception x) {
			log.error("Failed to serialize the session!", x);
		}
		return null;
	}

	protected Session readSession(byte[] data) {
		try {
			ByteArrayInputStream session_data = new ByteArrayInputStream(data);
			ReplicationStream session_in = new ReplicationStream(session_data,
					this.container.getLoader().getClassLoader());
			Session session = createSession(false, false);
			((ReplicatedSession) session).readObjectData(session_in);
			return session;
		} catch (Exception x) {
			log.debug("Failed to deserialize the session!", x);
		}
		return null;
	}

	@Override
	public void load() throws IOException, ClassNotFoundException{
		this.mManagerRunning = true;
		//super.load();
		try {
			if (this.mChannelStarted)
				return;

			this.channel = new JChannel(this.protoConfig);
			this.channel.connect("TOMCAT_SESSIONS");
			this.channel.setOpt(Channel.LOCAL, Boolean.FALSE);

			this.channel.setReceiver(new SessionMessageReceiver());
			
			this.mChannelStarted = true;
			log.debug("JGroups channel started inside tomcat");

			sendSessionEvent(new SessionMessage(Event.GET_ALL_SESSIONS, null, null, null, null, null));
		} catch (Exception x) {
			log.debug("Unable to start javagroups channel", x);
		}
	}

	@Override
	public void unload() throws IOException{
		this.mManagerRunning = false;
		this.mChannelStarted = false;
		//super.unload();
		
		try {
			this.channel.close();
			if (this.disp != null) {
				this.disp.stop();
				this.disp = null;
			}
		} catch (Exception x) {
			log.error("Unable to stop javagroups channel", x);
		}
	}

	protected void sendSessionEvent(SessionMessage msg) {
		if (!this.mChannelStarted) {
			log.debug("Channel is not active, not sending message=" + msg);
			return;
		}
		try {
			
	        ByteArrayOutputStream baos = null;
	        ObjectOutputStream os = null;
	        byte[] b = null;
	    	try{
		        baos = new ByteArrayOutputStream();
		        os = new ObjectOutputStream(baos);
		        os.writeObject(msg);
		        b = baos.toByteArray();
	    	}catch(Exception e){
	    		throw e;
	    	}finally{
	            if(os != null) os.close();
	            if(baos != null) baos.close();
	    	}
			
			Message jgmsg = new Message(null, this.channel.getLocalAddress(), b);
			
			this.channel.send(jgmsg);
			/**
			Message jgmsg = new Message(destination, this.mChannel.getLocalAddress(), msg);
			int mode = this.synchronousReplication ? 2 : 6;

			if (destination != null) {
				mode = 6;
				this.disp.sendMessage(jgmsg, mode, this.replicationTimeout);
			} else {
				this.disp.castMessage(null, jgmsg, mode, this.replicationTimeout);
			}
			**/
		} catch (Exception x) {
			log.error("Unable to send message through javagroups channel", x);
		}
	}

	class SessionMessageReceiver implements Receiver {

		public byte[] getState() {
			return null;
		}

		public void receive(Message message) {

			try {
				if (message.getSrc() == channel.getLocalAddress()) {
					return;
				}
				
				//SessionMessage msg = (SessionMessage)message.getObject();
				
		    	Object m = null;
		    	ByteArrayInputStream bais = null;
		    	ObjectInputStream in = null;
		    	try{
			        bais = new ByteArrayInputStream(message.getRawBuffer());
			        in = new ReplicationStream(bais, container.getLoader().getClassLoader());
			        m = in.readObject();
		    	}catch(Exception e){
		    		throw new RuntimeException(e);
		    	}finally{
			        if(in != null) in.close();
			        if(bais != null) bais.close();
		    	}
		    	
		    	SessionMessage msg = (SessionMessage)m;
				
				log.debug("Received SessionMessage of type=" + msg.getEventTypeString());

				ReplicatedSession session = null;
				boolean notify = false;

				switch (msg.getEventType()) {
				case ATTRIBUTE_ADDED:
					session = (ReplicatedSession) findSession(msg.getSessionID());
					if (session == null) {
						log.debug("Replicated session with ID{1} [" + msg.getSessionID() + "] doesn't exist!");
						return;
					}

					session.setAttribute(msg.getAttributeName(), msg.getAttributeValue(), false);
					break;
				case ATTRIBUTE_REMOVED_WONOTIFY:
				case ATTRIBUTE_REMOVED_WNOTIFY:
					notify = msg.getEventType() == Event.ATTRIBUTE_REMOVED_WNOTIFY;

					session = (ReplicatedSession) findSession(msg.getSessionID());
					if (session == null) {
						log.debug("Replicated session with ID{2} [" + msg.getSessionID() + "] doesn't exist!");
						return;
					}

					session.removeAttribute(msg.getAttributeName(), notify, false);
					break;
				case REMOVE_SESSION_NOTE:
					session = (ReplicatedSession) findSession(msg.getSessionID());
					if (session == null) {
						log.debug("Replicated session with ID{3} [" + msg.getSessionID() + "] doesn't exist!");
						return;
					}

					session.removeNote(msg.getAttributeName(), false);
					break;
				case SET_SESSION_NOTE:
					session = (ReplicatedSession) findSession(msg.getSessionID());
					if (session == null) {
						log.debug("Replicated session with ID{4} [" + msg.getSessionID() + "] doesn't exist!");
						return;
					}

					session.setNote(msg.getAttributeName(), msg.getAttributeValue(), false);
					break;
				case GET_ALL_SESSIONS:
					Object[] sessions = findSessions();
					for (int i = 0; i < sessions.length; i++) {
						if ((sessions[i] instanceof ReplicatedSession)) {
							ReplicatedSession ses = (ReplicatedSession) sessions[i];
							SessionMessage newmsg = new SessionMessage(Event.SESSION_CREATED, writeSession(ses), ses.getId(), null, null, null);

							sendSessionEvent(newmsg);

							if (ses.getPrincipal() != null) {
								SessionMessage pmsg = new SessionMessage(
										Event.SET_USER_PRINCIPAL,
										null,
										ses.getId(),
										null,
										null,
										SerializablePrincipal.createPrincipal((GenericPrincipal) ses.getPrincipal()));

								sendSessionEvent(pmsg);
							}
						} else {
							log.debug("System contains non standard sessions=" + sessions[i]);
						}
					}
					break;
				case SESSION_ACCESSED:
					session = (ReplicatedSession) findSession(msg.getSessionID());
					if (session == null) {
						log.debug("Replicated session with ID{5} [" + msg.getSessionID() + "] doesn't exist!");
						return;
					}
					session.access(false);
					break;
				case SET_USER_PRINCIPAL:
					session = (ReplicatedSession) findSession(msg.getSessionID());
					if (session == null) {
						log.debug("Replicated session with ID{6} [" + msg.getSessionID() + "] doesn't exist!");
						return;
					}

					GenericPrincipal principal = msg.getPrincipal().getPrincipal();
					session.setPrincipal(principal, false);
					break;
				case SESSION_CREATED:
					session = (ReplicatedSession) readSession(msg.getSession());
					session.setManager(JGroupsReplicationManager.this);
					add(session);
					break;
				case SESSION_EXPIRED_WONOTIFY:
				case SESSION_EXPIRED_WNOTIFY:
					notify = msg.getEventType() == Event.SESSION_EXPIRED_WNOTIFY;
					session = (ReplicatedSession) findSession(msg.getSessionID());
					if (session == null) {
						log.debug("Replicated session with ID{7} [" + msg.getSessionID() + "] doesn't exist!");
						return;
					}
					session.expire(notify, false);
				}

			} catch (Exception x) {
				log.error("Unable to receive message through javagroups channel", x);
			}
		}

		public void setState(byte[] state) {
			
		}

		public void viewAccepted(View new_view) {
			log.debug("Received membership message=" + new_view);
		}

		public void suspect(Address suspected_mbr) {
		}

		public void block() {
		}
	}

}