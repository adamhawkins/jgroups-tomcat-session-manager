package com.mobileiq;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import org.apache.catalina.Realm;
import org.apache.catalina.realm.GenericPrincipal;

public class SerializablePrincipal implements Serializable {
	protected String name = null;

	protected String password = null;

	protected String[] roles = new String[0];

	public SerializablePrincipal() {
	}

	public SerializablePrincipal(String name, String password) {
		this(name, password, null);
	}

	public SerializablePrincipal(String name, String password, List roles) {
		this.name = name;
		this.password = password;
		if (roles != null) {
			this.roles = new String[roles.size()];
			this.roles = ((String[]) roles.toArray(this.roles));
			if (this.roles.length > 0)
				Arrays.sort(this.roles);
		}
	}

	public String getName() {
		return this.name;
	}

	public String getPassword() {
		return this.password;
	}

	public String[] getRoles() {
		return this.roles;
	}

	public String toString() {
		StringBuffer sb = new StringBuffer("SerializablePrincipal[");
		sb.append(this.name);
		sb.append("]");
		return sb.toString();
	}

	public static SerializablePrincipal createPrincipal(GenericPrincipal principal) {
		if (principal == null)
			return null;
		return new SerializablePrincipal(principal.getName(), principal.getPassword(), principal.getRoles() != null ? Arrays.asList(principal.getRoles()) : null);
	}

	public GenericPrincipal getPrincipal() {
		return new GenericPrincipal(this.name, this.password, getRoles() != null ? Arrays.asList(getRoles()) : null);
	}
}