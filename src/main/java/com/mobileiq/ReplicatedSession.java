package com.mobileiq;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.security.Principal;

import org.apache.catalina.Manager;
import org.apache.catalina.realm.GenericPrincipal;
import org.apache.catalina.session.ManagerBase;
import org.apache.catalina.session.StandardSession;

import com.mobileiq.SessionMessage.Event;

public class ReplicatedSession extends StandardSession {
	private transient Manager mManager = null;

	public ReplicatedSession(Manager manager) {
		super(manager);
		this.mManager = manager;
	}

	public void access(boolean notify) {
		super.access();
		if (notify) sendMessage(new SessionMessage(Event.SESSION_ACCESSED, null, getId(), null,null, null));
	}

	public void access() {
		access(true);
	}

	public void expire(boolean notify, boolean jgnotify) {
		if ((((JGroupsReplicationManager) this.mManager).isManagerRunning())
				|| (((JGroupsReplicationManager) this.mManager)
						.getExpireSessionsOnShutdown())) {
			String id = getId();
			super.expire(notify);

			if (jgnotify) {
				SessionMessage msg = new SessionMessage(notify ? Event.SESSION_EXPIRED_WNOTIFY : Event.SESSION_EXPIRED_WONOTIFY, null, id, null, null, null);
				sendMessage(msg);
			}
			log("Expire called on session with jgnotify=" + jgnotify + " and notify=" + notify);
		} else {
			log("Expire will not be called on session since isRunning="
					+ ((JGroupsReplicationManager) this.mManager)
							.isManagerRunning()
					+ " and getExpireSessionsOnShutdown="
					+ ((JGroupsReplicationManager) this.mManager)
							.getExpireSessionsOnShutdown());
		}
	}

	public void expire() {
		expire(true, true);
	}

	public void expire(boolean notify) {
		expire(notify, true);
	}

	public void removeAttribute(String name, boolean notify, boolean jgnotify) {
		super.removeAttribute(name, notify);
		if (jgnotify) sendMessage(new SessionMessage(notify ? Event.ATTRIBUTE_REMOVED_WNOTIFY : Event.ATTRIBUTE_REMOVED_WONOTIFY, null, getId(), name, null, null));
	}

	public void removeAttribute(String name, boolean notify) {
		removeAttribute(name, notify, true);
	}


	public void setAttribute(String name, Object value) {
		if (!(value instanceof Serializable)) {
			throw new IllegalArgumentException("Attribute[" + name + "] is not serializable.");
		}

		super.setAttribute(name, value);

		sendMessage(new SessionMessage(Event.ATTRIBUTE_ADDED, null, getId(), name, value, null));
	}

	public Object getAttribute(String name) {
		Object value = super.getAttribute(name);
		if (value == null)
			return value;
		ClassLocalizer loc = new ClassLocalizer("org.apache.catalina.session.StandardSessionFacade");
		StringBuffer buf = new StringBuffer("getAttribute called:\n\t");
		buf.append("Name=").append(name).append("; value=").append(value);
		buf.append("\n\tDebug info=").append(loc.getFileName()).append(":").append(loc.getLineNumber()).append("\n");
		log(buf.toString());
		return value;
	}

	public void setManager(JGroupsReplicationManager mgr) {
		this.mManager = mgr;
		super.setManager(mgr);
	}

	public void setPrincipal(Principal principal) {
		setPrincipal(principal, true);
	}

	public void setPrincipal(Principal principal, boolean jgnotify) {
		super.setPrincipal(principal);
		if (jgnotify) sendMessage(new SessionMessage(Event.SET_USER_PRINCIPAL, null, getId(), null, null, SerializablePrincipal.createPrincipal((GenericPrincipal) principal)));
	}

	public void removeNote(String name, boolean jgnotify) {
		super.removeNote(name);
		if (jgnotify) sendMessage(new SessionMessage(Event.REMOVE_SESSION_NOTE, null, getId(), name,null, null));
	}

	public void removeNote(String name) {
		removeNote(name, false);
	}

	public void setNote(String name, Object value, boolean jgnotify) {
		super.setNote(name, value);
		if (jgnotify) sendMessage(new SessionMessage(Event.SET_SESSION_NOTE, null, getId(), name, value, null));
	}

	public void setNote(String name, Object value) {
		setNote(name, value, false);
	}

	public void readObjectData(ObjectInputStream stream)throws ClassNotFoundException, IOException {
		super.readObjectData(stream);
	}

	public void writeObjectData(ObjectOutputStream stream) throws IOException {
		super.writeObjectData(stream);
	}

	private void sendMessage(SessionMessage msg) {
		if ((this.mManager != null) && ((this.mManager instanceof JGroupsReplicationManager))) {
			JGroupsReplicationManager transport = (JGroupsReplicationManager) this.mManager;
			transport.sendSessionEvent(msg);
		} else {
			log("Not sending session event through javagroups - invalid manager=" + this.mManager);
		}
	}

	private void log(String message) {
		if ((this.mManager != null) && ((this.mManager instanceof JGroupsReplicationManager))){
			((JGroupsReplicationManager) this.mManager).log.debug("ReplicatedSession: " + message);
		}else{
			System.out.println("ReplicatedSession: " + message);
		}
	}

	private void log(String message, Throwable x) {
		if ((this.mManager != null) && ((this.mManager instanceof JGroupsReplicationManager))) {
			((JGroupsReplicationManager) this.mManager).log.debug("ReplicatedSession: " + message, x);
		} else {
			System.out.println("ReplicatedSession: " + message);
			x.printStackTrace();
		}
	}
	
	public String toString(){
		return "ReplicatedSession[" + this.getId() + "}";
	}

	public static class ClassLocalizer implements Serializable {
		transient String lineNumber;
		transient String fileName;
		transient String className;
		transient String methodName;
		public String fullInfo;
		private static StringWriter sw = new StringWriter();
		private static PrintWriter pw = new PrintWriter(sw);
		public static final String LINE_SEP = System
				.getProperty("line.separator");
		public static final int LINE_SEP_LEN = LINE_SEP.length();
		protected String s;
		public static final String NA = "?";

		static final long serialVersionUID = -1325822038990805636L;

		public ClassLocalizer(String fqnOfCallingClass) {
			Throwable t = new Throwable();

			synchronized (sw) {
				t.printStackTrace(pw);
				this.s = sw.toString();
				sw.getBuffer().setLength(0);
			}

			int ibegin = this.s.lastIndexOf(fqnOfCallingClass);
			if (ibegin == -1)
				return;

			ibegin = this.s.indexOf(LINE_SEP, ibegin);
			if (ibegin == -1)
				return;

			ibegin += LINE_SEP_LEN;
			int iend = this.s.indexOf(LINE_SEP, ibegin);
			if (iend == -1)
				return;

			this.fullInfo = this.s.substring(ibegin, iend);
		}

		public String getFullTrace() {
			return this.s;
		}

		public String getClassName() {
			if (this.fullInfo == null)
				return "?";
			if (this.className == null) {
				int iend = this.fullInfo.lastIndexOf('(');
				if (iend == -1) {
					this.className = "?";
				} else {
					iend = this.fullInfo.lastIndexOf('.', iend);
					int ibegin = 0;
					if (iend == -1)
						this.className = "?";
					else
						this.className = this.fullInfo.substring(ibegin, iend);
				}
			}
			return this.className;
		}

		public String getFileName() {
			if (this.fullInfo == null)
				return "?";
			if (this.fileName == null) {
				int iend = this.fullInfo.lastIndexOf(':');
				if (iend == -1) {
					this.fileName = "?";
				} else {
					int ibegin = this.fullInfo.lastIndexOf('(', iend - 1);
					this.fileName = this.fullInfo.substring(ibegin + 1, iend);
				}
			}
			return this.fileName;
		}

		public String getLineNumber() {
			if (this.fullInfo == null)
				return "?";
			if (this.lineNumber == null) {
				int iend = this.fullInfo.lastIndexOf(')');
				int ibegin = this.fullInfo.lastIndexOf(':', iend - 1);
				if (ibegin == -1)
					this.lineNumber = "?";
				else
					this.lineNumber = this.fullInfo.substring(ibegin + 1, iend);
			}
			return this.lineNumber;
		}

		public String getMethodName() {
			if (this.fullInfo == null)
				return "?";
			if (this.methodName == null) {
				int iend = this.fullInfo.lastIndexOf('(');
				int ibegin = this.fullInfo.lastIndexOf('.', iend);
				if (ibegin == -1)
					this.methodName = "?";
				else
					this.methodName = this.fullInfo.substring(ibegin + 1, iend);
			}
			return this.methodName;
		}
	}
}